# EV Charge — 모니터링 스택 (Prometheus + Grafana + exporters)

로컬 부하 테스트 시 k6·Spring·Postgres·Redis 메트릭을 한 화면에서 봅니다.

## 구성

| 서비스 | URL | 역할 |
|--------|-----|------|
| Grafana | http://localhost:3000 | 대시보드 (기본 `admin` / `admin`) |
| Prometheus | http://localhost:9090 | 메트릭 저장 + k6 remote write 수신 |
| postgres-exporter | :9187 | DB 커넥션 등 |
| redis-exporter | :9121 | Redis commands / clients |
| Spring Actuator | http://localhost:8080/actuator/prometheus | Hikari·JVM |

대시보드: Grafana → **EV Charge / EV Loadtest Overview**  
(공식 k6 대시보드도 Import 가능: ID `18030` — native histogram용)

## 기동

`ev` 폴더 · PowerShell:

```powershell
docker compose up -d
```

상태 확인:

```powershell
docker compose ps
# Prometheus targets: http://localhost:9090/targets
```

Spring 앱은 호스트에서 `./gradlew bootRun` (또는 Cursor EvApplication).  
Prometheus는 `host.docker.internal:8080`으로 Actuator를 scrape 합니다.

## k6 → Prometheus

`loadtest/run-with-grafana.ps1` 사용 (권장):

```powershell
# ev 폴더
.\loadtest\run-with-grafana.ps1 -Strategy REDISSON -Script auto-reserve.js
```

또는 수동:

```powershell
$env:K6_PROMETHEUS_RW_SERVER_URL = "http://localhost:9090/api/v1/write"
$env:K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM = "true"
k6 run -o experimental-prometheus-rw `
  -e STRATEGY=REDISSON `
  -e BASE_URLS=http://localhost:8080 `
  -e MAX_KM=0.5 `
  loadtest/auto-reserve.js
```

실행 중 Grafana에서 시간 범위를 **Last 15 minutes**로 두고 그래프를 확인하세요.

## 환경 변수

`.env` (선택):

```
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin
DB_PASSWORD=changeme
```

`DB_PASSWORD`는 Postgres와 postgres-exporter가 동일해야 합니다.

## 보안 참고

`/actuator/prometheus` 는 로컬 scrape를 위해 permitAll 입니다.  
공인 배포 시에는 Actuator를 외부에 열지 말고 VPN/방화벽으로 막으세요.
