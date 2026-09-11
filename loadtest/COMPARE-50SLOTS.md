# 50자리 × 50유저 — REDISSON vs PESSIMISTIC (Grafana 수치화)

## 고정 조건
| 항목 | 값 |
|------|-----|
| VU / 유저 | 50 (`loadtest/users.json`) |
| AVAILABLE | 정확히 50기 (`prepare-50-slots.sql`) |
| 반경 | `MAX_KM=1` |
| 앱 | 단일 `8080` |
| 스크립트 | `auto-reserve-50slots.js` |

## 캡처할 지표 (대시보드 번호)
1. **RPS** — k6 HTTP 처리량  
2. **VUs** — 동시 유저  
3. **Hikari connections** — 풀 active/pending/max  
4. **DB 커넥션 점유시간** — Hikari usage p95 + `ev_reserve_hold` p95  
5. **락 경합 후 재시도 성공률** — `retry_success / (retry_success + exhausted_conflict)`  
6. **락 대기시간 분포** — `ev_lock_wait` p50/p95 (`redisson` / `db_for_update`)

## 실행 순서 (`ev` 폴더 PowerShell)

### 0) 준비
- Docker: `docker compose up -d`
- 앱: `./gradlew bootRun` (또는 EvApplication) — **메트릭 반영 위해 재시작 필요**
- Grafana: http://localhost:3000/d/ev-loadtest-overview/ev-loadtest-overview

### 1) 50자리 세팅
```powershell
Get-Content loadtest/prepare-50-slots.sql -Raw | docker exec -i ev-charge-db psql -U ev_admin -d ev_charge
```

### 2) REDISSON 런 → 캡처
```powershell
.\loadtest\run-with-grafana.ps1 -Strategy REDISSON -Script auto-reserve-50slots.js -MaxKm 1
```
대시보드에서 1~6번 패널 스크린샷 → `loadtest/screenshots/50slots-redisson-*.png`

### 3) 리셋 후 동일 조건으로 PESSIMISTIC
```powershell
Get-Content loadtest/reset-loadtest-reservations.sql -Raw | docker exec -i ev-charge-db psql -U ev_admin -d ev_charge
Get-Content loadtest/prepare-50-slots.sql -Raw | docker exec -i ev-charge-db psql -U ev_admin -d ev_charge

.\loadtest\run-with-grafana.ps1 -Strategy PESSIMISTIC -Script auto-reserve-50slots.js -MaxKm 1
```
같은 패널 캡처 → `loadtest/screenshots/50slots-pessimistic-*.png`

### 4) 터미널 수치도 같이 기록
k6 종료 요약의 `auto_reserve_ok` / `conflict` / `fail` / duration 을 전략별로 표에 적기.
