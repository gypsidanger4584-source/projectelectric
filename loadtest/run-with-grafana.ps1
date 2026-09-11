# k6 → Prometheus remote write → Grafana
# 기본: 50자리 × 50유저 비교 런
#
# 예:
#   .\loadtest\run-with-grafana.ps1
#   .\loadtest\run-with-grafana.ps1 -Strategy PESSIMISTIC

param(
    [ValidateSet("REDISSON", "PESSIMISTIC")]
    [string]$Strategy = "REDISSON",

    [ValidateSet("auto-reserve.js", "auto-reserve-50slots.js")]
    [string]$Script = "auto-reserve-50slots.js",

    [string]$BaseUrls = "http://localhost:8080",

    [double]$MaxKm = 1,

    [string]$PrometheusRwUrl = "http://localhost:9090/api/v1/write"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "==> Prometheus remote write: $PrometheusRwUrl"
Write-Host "==> Strategy=$Strategy Script=$Script BaseUrls=$BaseUrls MaxKm=$MaxKm"
Write-Host "==> Grafana: http://localhost:3000/d/ev-loadtest-overview/ev-loadtest-overview"
Write-Host "==> Capture: RPS, VU, Hikari, pool occupancy, retry success, lock wait"

$env:K6_PROMETHEUS_RW_SERVER_URL = $PrometheusRwUrl
$env:K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM = "true"

k6 run -o experimental-prometheus-rw `
  -e "STRATEGY=$Strategy" `
  -e "BASE_URLS=$BaseUrls" `
  -e "MAX_KM=$MaxKm" `
  "loadtest/$Script"
