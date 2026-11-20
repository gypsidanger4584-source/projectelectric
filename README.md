# EvCharge (`ev`)

전기차 충전소 조회, 자동예약, 즐겨찾기를 제공하는 Spring Boot 애플리케이션입니다.
같은 충전기에 동시 예약이 몰려도 이중 예약이 생기지 않도록 만들었고, 비관적 락(DB `FOR UPDATE`)과 Redisson 분산 락을 런타임에 바꿔 가며 비교할 수 있습니다.
부하 테스트 결과는 [docs/loadtest-report.md](docs/loadtest-report.md)에 정리했습니다.

## 목차

1. [스택](#스택)
2. [사전 준비](#사전-준비)
3. [환경 변수](#환경-변수-env)
4. [실행 방법](#실행-방법)
5. [폴더 안내](#폴더-안내)
6. [동시성 · 락](#동시성--락)
7. [동시성 통합 테스트](#동시성-통합-테스트)
8. [부하 테스트 결과 요약](#부하-테스트-결과-요약)
9. [모니터링](#모니터링-prometheus--grafana)
10. [부하 테스트 실행](#부하-테스트-실행)
11. [CI](#ci)

## 스택

| 구분 | 기술 |
|------|------|
| App | Java 17, Spring Boot 4, Spring Security(세션), Spring Data JPA, Thymeleaf |
| DB / Cache | PostgreSQL 16, Redis 7 (Redisson) |
| 스케줄러 | `@Scheduled` + ShedLock (서버가 여러 대여도 한 대만 실행) |
| 외부 API | 카카오맵 JS, 카카오 REST/Mobility, 공공데이터 충전소 |
| 부하 테스트 | k6 (`loadtest/`) |
| 모니터링 | Prometheus, Grafana (`monitoring/`) |
| 인프라 / CI | Docker Compose, GitHub Actions |

## 사전 준비

- Java 17 이상
- Docker Desktop
- Gradle Wrapper (`gradlew`, `gradlew.bat`)
- 부하 테스트를 돌릴 때만 [k6](https://k6.io/)

## 환경 변수 (`.env`)

시크릿은 커밋하지 않습니다. `.env.example`을 복사해 `.env`를 만들고 값을 채웁니다.

```powershell
# Windows, ev 폴더
copy .env.example .env
```

```bash
# macOS / Linux
cp .env.example .env
```

| 변수 | 용도 |
|------|------|
| `KAKAO_MAPS_API_KEY` | 카카오맵 JavaScript 키 |
| `KAKAO_REST_API_KEY` | 카카오 REST / Mobility |
| `PUBLIC_DATA_API_KEY` | 공공데이터 충전소 API |
| `DB_PASSWORD` | PostgreSQL (`docker-compose`와 앱 공통) |
| `SESSION_COOKIE_SECURE` | HTTPS면 `true`, 로컬은 `false` |
| `APP_ADMIN_LOCK_STRATEGY_TOGGLE` | `true`면 락 전략 전환 API 활성화 (기본 `false`) |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | Grafana 로그인 (기본 `admin` / `admin`) |

`application.yml`은 `${KAKAO_MAPS_API_KEY:}`처럼 환경 변수만 참조합니다. 로컬에서는 `springboot4-dotenv`가 `.env`를 읽습니다.

## 실행 방법

`ev` 폴더에서 실행합니다.

### 1) 인프라

```powershell
docker compose up -d
```

Postgres, Redis, Prometheus, Grafana, exporter가 함께 올라옵니다. DB와 Redis만 켜려면 다음처럼 합니다.

```powershell
docker compose start postgres redis
# 끌 때 (컨테이너 유지): docker compose stop postgres redis
```

### 2) 앱

```powershell
.\gradlew.bat bootRun
```

### 3) 접속

| URL | 용도 |
|-----|------|
| http://localhost:8080 | EvCharge 앱 |
| http://localhost:3000 | Grafana |
| http://localhost:9090 | Prometheus |

compose의 모든 포트는 `127.0.0.1`에만 열립니다.

## 폴더 안내

| 경로 | 내용 |
|------|------|
| `src/` | 애플리케이션 코드. `service/` 예약 로직, `lock/` 락 전략, `security/` 로그인 제한 |
| `loadtest/` | k6 스크립트, 시드·리셋 SQL, Grafana 캡처 |
| `monitoring/` | Prometheus, Grafana 설정 파일 |
| `docs/` | 부하 테스트 보고서, 설계 메모 |
| `.github/workflows/` | CI 설정 |

## 동시성 · 락

| 항목 | 내용 |
|------|------|
| 점유 TX | `reserveCharger`는 `REQUIRES_NEW`로 열고 `FOR UPDATE`로 충전기 행을 다시 읽음 |
| 자동예약 | 오케스트레이션은 `NOT_SUPPORTED`, 409가 나면 다음 후보로 폴백 |
| Redisson | 커밋 또는 롤백이 끝난 뒤 unlock (`afterCompletion`) |
| DB 방어 | `UNIQUE (charger_id) WHERE status = 'PENDING'` |
| 만료 처리 | ShedLock으로 한 서버만 실행하고, `UPDATE ... WHERE status = 'PENDING'`으로 중복 처리를 막음 |
| 취소 | 만료와 같은 조건부 UPDATE로 처리해 서로 덮어쓰지 않게 함 |
| 락 전략 | 기본값은 `PESSIMISTIC`. `POST /api/admin/lock-strategy`로 `REDISSON`과 전환하며, `APP_ADMIN_LOCK_STRATEGY_TOGGLE=true`일 때만 열림 |

## 동시성 통합 테스트

`ReservationConcurrencyIntegrationTest`가 실제 PostgreSQL과 Redis 위에서 아래를 검증합니다. CI에서는 서비스 컨테이너를, 로컬에서는 `docker compose up -d postgres redis`로 띄운 컨테이너를 씁니다.

| 검증 | 내용 |
|------|------|
| 동시 예약 | 같은 충전기에 20명이 동시에 예약하면 PESSIMISTIC, REDISSON 모두 1건만 성공하고 나머지는 409. DB의 PENDING도 1건 |
| partial unique | 같은 충전기에 PENDING을 하나 더 넣으면 DB가 거절. 이전 예약이 EXPIRED면 새 PENDING은 허용 |
| 취소 vs 만료 | 만료가 먼저 이기면 취소는 409이고 EXPIRED가 유지됨. 취소가 먼저 이기면 만료 UPDATE는 0건 |

```powershell
docker compose up -d postgres redis
.\gradlew.bat test --tests "*ReservationConcurrencyIntegrationTest"
```

취소와 만료가 정확히 같은 순간에 부딪히는 경합을 반복 실행으로 재현하지는 않았습니다. 위 테스트는 어느 쪽이 먼저 이겼을 때의 결과를 DB 위에서 고정해 둔 것입니다.

## 부하 테스트 결과 요약

k6로 자동예약 API에 동시 요청을 보냈습니다. 상세 조건과 수치는 [보고서](docs/loadtest-report.md)에 있습니다.

처음 측정에서 k6는 50건 모두 성공으로 나왔지만, DB에서는 한 충전기에 PENDING 예약이 최대 20건 잡혀 있었습니다. HTTP 성공률만 보면 놓치는 문제여서 이후에는 충전기당 PENDING 수를 DB에서 직접 확인했습니다.

원인은 세 가지였습니다. 긴 트랜잭션에서 읽은 충전기 상태가 캐시에 남아 검증이 낡은 값으로 이뤄졌고, Redisson unlock이 DB 커밋보다 먼저 실행될 수 있었으며, DB에 충전기당 PENDING 1건을 강제하는 제약이 없었습니다.

| 단계 | 내용 | 충전기당 최대 PENDING |
|------|------|--------------------:|
| 수정 전 | k6 ok 50, DB 이중 예약 발생 | 20 |
| 정합성 수정 | 락 후 재조회, 커밋 뒤 unlock, partial unique. 성공 1건, 나머지 409 | 1 |
| 폴백 추가 | 409가 나면 다음 후보 시도. 바깥 TX를 정리하기 전에는 Hikari 풀 고갈로 전부 실패 | 1 |
| 50자리 x 50유저 | ok 49, 마지막 성공까지 5.7초 | 1 |

수정 전 측정은 충전소 5곳으로 한정한 조건(VU 50)이고, 이후 단계는 조건이 달라서 단계 간 수치를 같은 조건의 비교로 읽으면 안 됩니다.

같은 조건(VU 50, 충전기 50기, 단일 인스턴스)에서 락 전략만 바꿔 비교한 결과입니다.

| 지표 | REDISSON | PESSIMISTIC |
|------|---------:|------------:|
| 성공 / DB 최대 PENDING | 50 / 1 | 50 / 1 |
| 요청 평균 | 5,331 ms | 2,282 ms |
| 요청 p95 | 6,848 ms | 3,234 ms |
| 마지막 성공까지 | 8.776 s | 4.197 s |

단일 인스턴스에서는 PESSIMISTIC이 더 빨랐습니다. 서버를 여러 대 띄웠을 때 Redisson이 어떻게 나오는지는 아직 측정하지 않았습니다.

## 모니터링 (Prometheus + Grafana)

```
k6 --(remote write)--> Prometheus <-- scrape -- Spring Actuator / exporters
                           |
                           v
                        Grafana
```

| 서비스 | URL | 역할 |
|--------|-----|------|
| Grafana | http://localhost:3000 | 대시보드 |
| Prometheus | http://localhost:9090 | 메트릭 저장, k6 remote write 수신 |
| postgres-exporter | :9187 | DB 커넥션 등 |
| redis-exporter | :9121 | Redis 명령 수, 클라이언트 수 |
| Spring Actuator | http://localhost:8080/actuator/prometheus | Hikari, JVM |

대시보드는 Grafana의 `EV Charge / EV Loadtest Overview`입니다. 공식 k6 대시보드는 ID `18030`으로 import할 수 있습니다. 설정 파일은 `monitoring/`에 있고, 앱 메트릭은 Prometheus가 `host.docker.internal:8080`으로 가져갑니다.

`/actuator/prometheus`는 로컬 확인용으로 인증 없이 열려 있습니다. 배포할 때는 반드시 막아야 합니다. `POST /api/stations/refresh`는 로그인이 필요하고 사용자당 분당 5회로 제한됩니다.

## 부하 테스트 실행

| 파일 | 설명 |
|------|------|
| `loadtest/auto-reserve.js` | 일반 A/B 부하 |
| `loadtest/auto-reserve-50slots.js` | 50자리 경합, 마지막 성공 시각 측정 |
| `loadtest/run-with-grafana.ps1` | k6 결과를 Prometheus로 전송 |
| `loadtest/prepare-50-slots.sql`, `reset-loadtest-reservations.sql` | 충전기 50기 고정, 리셋 |
| `loadtest/COMPARE-50SLOTS.md` | 비교 실행 체크리스트 |
| `loadtest/screenshots/` | Grafana 캡처 |

락 전략을 바꿔 가며 돌리려면 앱을 `APP_ADMIN_LOCK_STRATEGY_TOGGLE=true`로 띄워야 합니다.

```powershell
docker cp loadtest/prepare-50-slots.sql ev-charge-db:/tmp/p50.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/p50.sql

.\loadtest\run-with-grafana.ps1 -Strategy REDISSON -Script auto-reserve-50slots.js -MaxKm 1

docker cp loadtest/reset-loadtest-reservations.sql ev-charge-db:/tmp/reset.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/reset.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/p50.sql
.\loadtest\run-with-grafana.ps1 -Strategy PESSIMISTIC -Script auto-reserve-50slots.js -MaxKm 1
```

## CI

GitHub Actions(`.github/workflows/ci.yml`)가 Postgres와 Redis 서비스 컨테이너를 띄우고, API 키와 `DB_PASSWORD`를 Secrets로 주입한 뒤 `./gradlew build`를 실행합니다.
