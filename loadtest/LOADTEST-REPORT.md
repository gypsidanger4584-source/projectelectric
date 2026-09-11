# EV 자동예약 동시성·락 부하 테스트 결과 보고서

작성일: 2026-09-10  
대상 프로젝트: `ev` (Spring Boot + PostgreSQL + Redis/Redisson)  
측정 도구: k6 v2.x, Docker (`ev-charge-db`, `ev-redis`)

---

## 1. 목적

1. 자동예약(`POST /api/reservations/auto`) 동시 요청에서 **이중 예약**이 발생하는지 검증한다.
2. **비관적 락(DB `FOR UPDATE`)** 과 **Redisson 분산 락**을 비교한다.
3. 결함 수정·409 폴백 적용 후, **성공률·지연·DB 불변조건**이 어떻게 바뀌는지 수치로 남긴다.
4. 자리 수 = 유저 수(50)인 경합에서 **마지막 성공 예약까지 걸린 시간**을 측정한다.

---

## 2. 공통 실험 조건

| 항목 | 값 |
|------|-----|
| 가상 사용자(VU) | loadtest 유저 (초기 10 → 이후 **50**) |
| 비밀번호 | `test1234` (로그인 username = email) |
| 좌표 | 서울시청 인근 `37.566826`, `126.9786567` (k6 스크립트 고정값, 브라우저 GPS와 무관) |
| 반경 | `maxDistanceKm` **0.5** 또는 **1.0** |
| 앱 | `8080` / `8081` 듀얼 또는 단일 `8080` |
| Redis | `localhost:6379` (`ev-redis`) |
| DB | PostgreSQL `ev_charge` (`ev-charge-db`) |
| 스크립트 | `loadtest/auto-reserve.js`, `loadtest/auto-reserve-50slots.js` |

### 2.1 시드·준비 SQL

| 파일 | 역할 |
|------|------|
| `seed-loadtest-users-10.sql` / `seed-loadtest-users-50.sql` | 테스트 유저·차량 생성 |
| `reset-loadtest-reservations.sql` | loadtest PENDING 취소·충전기 AVAILABLE 복구 |
| `prepare-50-slots.sql` | 유저 차종 통일 + AVAILABLE **50기** 고정 |

---

## 3. 발견한 핵심 결함 (이중 예약)

### 3.1 현상 (수정 전, 충전소 5곳 한정 · VU 50 · REDISSON/PESSIMISTIC)

**k6 HTTP 지표만 보면 “전부 성공”처럼 보임.**

| 전략 | ok | conflict | fail |
|------|---:|---------:|-----:|
| REDISSON | **50** | 0 | 0 |
| PESSIMISTIC | **50** | 0 | 0 |

**DB 실측은 불변조건 위반.**

| DB 지표 | 값 |
|---------|-----|
| PENDING 건수 | 50 |
| DISTINCT `charger_id` | **6** |
| **한 충전기당 최대 PENDING** | **20** (예: `charger_id=135049`) |

→ API는 201을 반환했지만, **충전기 1기 = PENDING 1건** 규칙이 깨짐.

### 3.2 원인 요약

| # | 원인 | 설명 |
|---|------|------|
| 1 | 긴 `@Transactional` + L1 캐시 | `autoReserve`에서 후보를 `AVAILABLE`로 로드한 뒤, 같은 TX의 `findById`가 **stale status**로 가드 |
| 2 | Redisson unlock 타이밍 | `finally` unlock이 **DB 커밋보다 앞설 수 있음** (TOCTOU) |
| 3 | DB 최후 방어 부재 | `charger_id`당 PENDING 1건을 강제하는 제약 없음 |

### 3.3 교훈

> 부하 테스트는 **HTTP 성공률만으로 판단하면 안 된다.**  
> `GROUP BY charger_id HAVING count(*) > 1` 같은 **DB 불변조건 검증**이 필수다.

---

## 4. 코드 수정 내역

### 4.1 정합성 (이중 예약 방지)

| 수정 | 내용 |
|------|------|
| `ReservationService.reserveCharger` | `propagation = REQUIRES_NEW` — 후보조회 TX와 분리 |
| 상태 재조회 | 락 후 `findByIdForUpdate`로 **fresh** 읽기 |
| 가드 강화 | 동일 충전기 `PENDING` 존재 시 409 |
| `RedissonLockStrategy` | unlock을 `TransactionSynchronization.afterCompletion`으로 **커밋/롤백 이후** |
| DB | `UNIQUE (charger_id) WHERE status = 'PENDING'` (`uq_reservations_charger_pending`) |
| Hikari / Postgres | 풀·`max_connections` 상향 (동시 FOR UPDATE 대기 대비) |

### 4.2 UX·처리량 (409 폴백)

| 수정 | 내용 |
|------|------|
| `AutoReservationService` | `reachable` 순회, **409면 다음 후보** 시도 |
| 오케스트레이션 TX | `NOT_SUPPORTED` — Kakao/폴백 루프가 커넥션을 오래 붙잡지 않음 |

### 4.3 부하 측정용 옵션

| 설정 | 의미 |
|------|------|
| `loadtest.skip-kakao: true` | 순위만 직선거리 (락·폴백·예약 로직은 동일). 50 동시 Kakao 호출 병목 회피 |

---

## 5. 수치 변화 타임라인

실험 축: **VU 50 · 반경 0.5~1km · 핫스팟(AVAILABLE 제한)**  
비교 단위: k6 카운터 + DB `max(PENDING per charger)`.

### 5.1 단계 A — 수정 전 (이중 예약)

| 지표 | REDISSON | PESSIMISTIC |
|------|---------:|------------:|
| ok | 50 | 50 |
| conflict | 0 | 0 |
| fail | 0 | 0 |
| **max PENDING / charger** | **20** | **20** (동일 패턴) |
| DISTINCT chargers (PENDING) | 6 | 6 |

### 5.2 단계 B — 정합성만 수정 (폴백 없음)

| 지표 | REDISSON | PESSIMISTIC |
|------|---------:|------------:|
| ok | **1** | **1** |
| conflict | 3 | 11 |
| fail | 46 | 38 |
| **max PENDING / charger** | **1** | **1** |
| dup chargers (`count>1`) | **0** | **0** |
| auto duration avg | ~26.9s | ~28.5s |

해석:
- 이중 예약 **해소**
- 전원 동일 1등 충전기만 시도 → 성공 1건, 나머지 409/404
- 락 대기로 latency 급증

### 5.3 단계 C — 폴백만 추가, 바깥 TX 미정리 (실패 런)

| 지표 | 값 |
|------|-----|
| ok | **0** |
| fail | **50** |
| duration avg | **~30.7s** |
| 서버 로그 | `HikariPool … Connection is not available, request timed out after ~30000ms` |

원인: 바깥 TX가 커넥션을 잡은 채 `REQUIRES_NEW` 반복 → **풀 고갈**.

### 5.4 단계 D — 폴백 + `NOT_SUPPORTED` + pool 보강 (REDISSON, AVAILABLE≈12 · 5곳)

| 지표 | 정합성만(B) | 폴백+TX 정리(D) |
|------|------------:|----------------:|
| ok | 1 | **6** |
| fail | 46 | 44 |
| pending | 1 | **6** |
| DISTINCT chargers | 1 | **6** |
| **max PENDING / charger** | **1** | **1** |
| duration avg | ~27s | **~11.2s** |

해석: 1등이 막히면 차순위로 넘어가 **서로 다른 6기**까지 채움. 이중 예약은 계속 0.

---

## 6. 50자리 × 50유저 — 마지막 성공까지 시간

### 6.1 설정

| 항목 | 값 |
|------|-----|
| 유저 | 50 (전원 **현대 아이오닉5**) |
| AVAILABLE | **정확히 50기** (타입 02/04, 시청 박스 내 최단거리순) |
| 전략 | REDISSON + 409 폴백 |
| 순위 | `loadtest.skip-kakao=true` (직선거리) |
| 앱 | **단일 8080** (PG `max_connections=200`, Hikari 70) |
| 스크립트 | `loadtest/auto-reserve-50slots.js` |

### 6.2 결과 (최종 성공 런)

| 지표 | 값 |
|------|-----|
| ok | **49** |
| conflict | **1** |
| fail | 0 |
| PENDING / DISTINCT charger | **49 / 49** |
| max PENDING / charger | **1** |
| **첫 성공 (start 기준)** | **2.748 s** (2,748 ms) |
| **마지막 성공 (start 기준)** | **5.688 s** (5,688 ms) |
| 첫~마지막 성공 간격 | **2.940 s** |
| 요청당 duration avg | **3.609 s** |
| 요청당 duration p95 | **4.872 s** |

해석:
- 50명이 동시에 달려들어, **마지막(49번째) 성공 확정까지 약 5.7초**.
- 1명은 자리 소진 후 409 (경합 타이밍).
- DB상 이중 예약 **0**.

### 6.3 참고: Kakao 미스킵 시

50 동시 Kakao 다중목적지 호출 + 커넥션 경합으로 ok가 11~26 수준에 머물고 duration ~30s대(타임아웃)가 관측됨.  
**락/폴백 로직 측정을 위해** 최종 50×50 런에서는 Kakao만 스킵했다.

---

## 7. 한눈에 보는 전체 변화

```
[수정 전]
  k6: ok 50 / conflict 0
  DB: 충전기 1기당 PENDING 최대 20     ← 사고

[정합성 수정]
  k6: ok 1 / conflict·fail 다수
  DB: 충전기당 PENDING 최대 1          ← 안전, UX 부족

[폴백 + 오케스트레이션 TX 제거]
  k6: ok 6 (핫스팟 12기 중 호환분)
  DB: PENDING 6기, max 1               ← 안전 + 차선 예약

[50자리 × 50유저]
  k6: ok 49 / conflict 1
  마지막 성공까지 ≈ 5.7초
  DB: max PENDING/charger = 1
```

---

## 8. 재현 방법 (요약)

**위치: PC PowerShell · `ev` 폴더**

```powershell
# 1) DB 준비 (Docker)
docker cp loadtest/prepare-50-slots.sql ev-charge-db:/tmp/p50.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/p50.sql

# 2) 앱 (예시: 8080)
.\gradlew.bat bootRun --args="--server.port=8080"

# 3) k6
k6 run -e STRATEGY=REDISSON -e BASE_URLS=http://localhost:8080 -e MAX_KM=1 loadtest/auto-reserve-50slots.js

# 4) DB 불변조건
docker exec ev-charge-db psql -U ev_admin -d ev_charge -c "
SELECT count(*) AS pending,
       count(DISTINCT charger_id) AS chargers,
       coalesce(max(cnt),0) AS max_per
FROM (
  SELECT charger_id, count(*) cnt
  FROM reservations r
  JOIN users u ON u.id = r.user_id
  WHERE u.email LIKE 'loadtest%' AND r.status = 'PENDING'
  GROUP BY charger_id
) t;"
```

**모니터링 (PC PowerShell · 아무 곳)**

```powershell
docker stats ev-charge-db ev-redis
```

---

## 9. 결론

1. **HTTP ok 50 ≠ 정합성 확보.** 수정 전 max PENDING/charger = **20**.
2. `REQUIRES_NEW` + `FOR UPDATE` fresh read + 커밋 후 Redis unlock + partial unique로 **max PENDING/charger = 1** 달성.
3. 409 폴백으로 핫스팟에서 성공이 **1 → 6**(제한된 AVAILABLE)으로 증가, 50×50에서는 **49 성공**.
4. 50자리 경합에서 **마지막 성공까지 약 5.7초**(첫 성공 2.7초, 간격 2.9초).
5. 자동예약 오케스트레이션은 **긴 TX를 열지 않는 것**이 커넥션 풀·락 대기 안정성에 중요했다.

---

## 10. 관련 파일

| 경로 | 설명 |
|------|------|
| `src/main/java/.../service/ReservationService.java` | REQUIRES_NEW, FOR UPDATE 예약 |
| `src/main/java/.../service/AutoReservationService.java` | NOT_SUPPORTED, 409 폴백, skip-kakao |
| `src/main/java/.../lock/RedissonLockStrategy.java` | afterCompletion unlock |
| `src/main/resources/application.yml` | redis, hikari, `loadtest.skip-kakao` |
| `loadtest/auto-reserve.js` | 일반 A/B 부하 |
| `loadtest/auto-reserve-50slots.js` | 50자리 마지막 성공 시각 측정 |
| `docker-compose.yml` | postgres `max_connections=200` |
