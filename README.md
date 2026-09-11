# EvCharge (`ev`)

전기차 충전소 **조회 · 자동예약 · 즐겨찾기** Spring Boot 애플리케이션입니다.

동시 예약 경합에서 **이중 예약을 막고**, **비관적 락(DB `FOR UPDATE`) vs Redisson 분산 락**을 런타임에 전환·비교할 수 있습니다.

---

## 스택

| 구분 | 기술 |
|------|------|
| App | Java 17, Spring Boot 4, Security(세션), JPA, Thymeleaf |
| DB / Cache | PostgreSQL 16, Redis 7 (Redisson) |
| 외부 API | 카카오맵 JS · 카카오 REST/Mobility · 공공데이터 충전소 |
| 부하 테스트 | k6 (`loadtest/`) |
| 모니터링 | Prometheus + Grafana (`monitoring/`) |

---

## 사전 준비

- Java 17+
- Docker Desktop
- Gradle Wrapper (`gradlew` / `gradlew.bat`)
- (부하 테스트 시) [k6](https://k6.io/)

---

## 환경 변수 (`.env`)

시크릿은 커밋하지 않습니다. **`.env.example`을 복사해 `.env`를 만들고** 값을 채우세요.

```powershell
# Windows · ev 폴더
copy .env.example .env
```

```bash
# macOS/Linux
cp .env.example .env
```

| 변수 | 용도 |
|------|------|
| `KAKAO_MAPS_API_KEY` | 카카오맵 JavaScript 키 |
| `KAKAO_REST_API_KEY` | 카카오 REST / Mobility |
| `PUBLIC_DATA_API_KEY` | 공공데이터 충전소 API |
| `DB_PASSWORD` | PostgreSQL (`docker-compose` · 앱 공통) |
| `SESSION_COOKIE_SECURE` | HTTPS면 `true` (로컬 `false`) |
| `GRAFANA_ADMIN_USER` / `PASSWORD` | Grafana 로그인 (기본 `admin`/`admin`) |

`application.yml`은 `${KAKAO_MAPS_API_KEY:}` 형태로만 참조합니다.  
로컬에서는 `springboot4-dotenv`가 `.env`를 로드하고, Cursor는 `.vscode/launch.json`의 `envFile`도 사용합니다.

---

## 실행 방법

**위치: `ev` 폴더**

### 1) 인프라

```powershell
docker compose up -d
```

기동되는 것: Postgres, Redis, Prometheus, Grafana, exporters

### 2) 앱

```powershell
.\gradlew.bat bootRun
```

또는 Cursor **Run and Debug → EvApplication**

### 3) 접속

| URL | 용도 |
|-----|------|
| http://localhost:8080 | EvCharge 앱 |
| http://localhost:3000 | Grafana (`admin` / `admin`) |
| http://localhost:9090 | Prometheus |

---

## 폴더 안내

| 경로 | 역할 |
|------|------|
| `loadtest/` | **어제~오늘 부하 테스트 본편** — k6 스크립트, SQL, 보고서, 스크린샷 |
| `monitoring/` | **오늘 추가** — Prometheus/Grafana 설정·대시보드 (그래프 도구) |
| `docs/` | 리팩터링·설계 메모 |
| `src/` | 애플리케이션 코드 |

혼동 포인트: **수치·시나리오·보고서는 `loadtest`**, **그래프 서버 설정은 `monitoring`**.

---

## 부하 테스트 (`loadtest/`)

### 목적

1. 자동예약 동시 요청에서 **이중 예약**이 없는지 (DB: charger당 PENDING ≤ 1)
2. **REDISSON vs PESSIMISTIC** 지연·처리량 비교
3. Grafana로 RPS · VU · Hikari · 락 대기 · 재시도율 시각화

### 주요 파일

| 파일 | 설명 |
|------|------|
| `LOADTEST-REPORT.md` | **결과 보고서** (결함 → 수정 → 50×50 → Grafana 비교) |
| `COMPARE-50SLOTS.md` | 50자리 비교 실행 체크리스트 |
| `auto-reserve-50slots.js` | 50 VU · 마지막 성공 시각 측정 |
| `run-with-grafana.ps1` | k6 → Prometheus remote write |
| `prepare-50-slots.sql` / `reset-loadtest-reservations.sql` | 자리 고정 · 리셋 |
| `screenshots/` | Grafana 캡처 |

### 빠른 비교 런 (50자리)

한글 SQL은 PowerShell 파이프보다 **docker cp**가 안전합니다.

```powershell
# 시드(최초 1회) · 50자리 준비
docker cp loadtest/seed-loadtest-users-10.sql ev-charge-db:/tmp/seed10.sql
docker cp loadtest/seed-loadtest-users-50.sql ev-charge-db:/tmp/seed50.sql
docker cp loadtest/prepare-50-slots.sql ev-charge-db:/tmp/p50.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/seed10.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/seed50.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/p50.sql

# REDISSON
.\loadtest\run-with-grafana.ps1 -Strategy REDISSON -Script auto-reserve-50slots.js -MaxKm 1

# 리셋 후 PESSIMISTIC
docker cp loadtest/reset-loadtest-reservations.sql ev-charge-db:/tmp/reset.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/reset.sql
docker exec ev-charge-db psql -U ev_admin -d ev_charge -f /tmp/p50.sql
.\loadtest\run-with-grafana.ps1 -Strategy PESSIMISTIC -Script auto-reserve-50slots.js -MaxKm 1
```

Grafana 대시보드: [EV Loadtest Overview](http://localhost:3000/d/ev-loadtest-overview/ev-loadtest-overview)

자세한 수치·해석·스크린샷: **[`loadtest/LOADTEST-REPORT.md`](loadtest/LOADTEST-REPORT.md)**

---

## 모니터링 (`monitoring/`)

```
k6 ──(remote write)──► Prometheus ◄── scrape ── Spring Actuator / exporters
                              │
                              ▼
                           Grafana
```

- 설정·타깃: [`monitoring/README.md`](monitoring/README.md)
- 앱 메트릭: `http://localhost:8080/actuator/prometheus`
- 로컬 전용으로 `/actuator/prometheus`, `/api/admin/lock-strategy` 는 인증 없이 열려 있습니다. **배포 시 반드시 막으세요.**

---

## 동시성 · 락 (요약)

| 항목 | 내용 |
|------|------|
| 점유 TX | `reserveCharger` → `REQUIRES_NEW` + `FOR UPDATE` fresh read |
| 자동예약 | 오케스트레이션 `NOT_SUPPORTED`, **409면 다음 후보 폴백** |
| Redisson | 커밋/롤백 후 unlock (`afterCompletion`) |
| DB 방어 | `UNIQUE (charger_id) WHERE status = 'PENDING'` |
| 전략 전환 | `POST /api/admin/lock-strategy` `{ "strategy": "REDISSON" \| "PESSIMISTIC" }` |

---

## CI

GitHub Actions (`.github/workflows/ci.yml`): Postgres + Redis 서비스 컨테이너, Secrets로 API 키·`DB_PASSWORD` 주입 후 `./gradlew build`.

---

## 문서 인덱스

| 문서 | 내용 |
|------|------|
| [`loadtest/LOADTEST-REPORT.md`](loadtest/LOADTEST-REPORT.md) | 부하 테스트 최종 보고서 |
| [`loadtest/COMPARE-50SLOTS.md`](loadtest/COMPARE-50SLOTS.md) | 50×50 비교 절차 |
| [`monitoring/README.md`](monitoring/README.md) | Grafana/Prometheus 사용법 |
| [`docs/refactoring/2026-09-11-reservation-domain-A.md`](docs/refactoring/2026-09-11-reservation-domain-A.md) | 예약 도메인 리팩터링 A |
