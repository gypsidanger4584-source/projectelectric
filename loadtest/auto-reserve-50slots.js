/**
 * 50 users vs 50 slots — measure time until last (50th) success.
 *
 * PC PowerShell · ev 폴더:
 *   k6 run -e STRATEGY=REDISSON -e BASE_URLS=http://localhost:8080,http://localhost:8081 -e MAX_KM=1 loadtest/auto-reserve-50slots.js
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Trend } from "k6/metrics";

const PASSWORD = "test1234";

const USERS = new SharedArray("users", function () {
  return JSON.parse(open("./users.json"));
});

const BASE_URLS = String(__ENV.BASE_URLS || "http://localhost:8080")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

const STRATEGY = (__ENV.STRATEGY || "REDISSON").toUpperCase();
const LAT = Number(__ENV.LAT || 37.566826);
const LNG = Number(__ENV.LNG || 126.9786567);
// 50기 선정 박스가 넓어서 1km 권장
const MAX_KM = Number(__ENV.MAX_KM || 1);

const autoOk = new Counter("auto_reserve_ok");
const autoConflict = new Counter("auto_reserve_conflict");
const autoFail = new Counter("auto_reserve_fail");
const autoDuration = new Trend("auto_reserve_duration", true);
/** 테스트 시작 시각부터 각 성공 예약까지 경과(ms). max ≈ 마지막(50번째) 성공 시각 */
const okElapsedFromStart = new Trend("ok_elapsed_from_start", true);

export const options = {
  scenarios: {
    spike: {
      executor: "per-vu-iterations",
      vus: USERS.length,
      iterations: 1,
      maxDuration: "5m",
    },
  },
};

function baseForVu(vu) {
  return BASE_URLS[(vu - 1) % BASE_URLS.length];
}

function extractCsrf(body) {
  const m = String(body).match(/name="_csrf"\s+value="([^"]+)"/);
  return m ? m[1] : null;
}

function login(base, email) {
  const page = http.get(`${base}/login`, { tags: { name: "login_page" } });
  const csrf = extractCsrf(page.body);
  if (!csrf) return false;
  const res = http.post(
    `${base}/login`,
    { username: email, password: PASSWORD, _csrf: csrf },
    { redirects: 0, tags: { name: "login" } }
  );
  return res.status === 302 || res.status === 303;
}

export function setup() {
  console.log(
    `users=${USERS.length} STRATEGY=${STRATEGY} MAX_KM=${MAX_KM} bases=${BASE_URLS.join(",")}`
  );
  for (const base of BASE_URLS) {
    if (!login(base, USERS[0].email)) {
      console.log(`setup login failed for ${base}`);
      continue;
    }
    const res = http.post(
      `${base}/api/admin/lock-strategy`,
      JSON.stringify({ strategy: STRATEGY }),
      { headers: { "Content-Type": "application/json" }, tags: { name: "lock_strategy_setup" } }
    );
    console.log(`setup ${base} STRATEGY=${STRATEGY} status=${res.status}`);
  }
  // setup 끝 직후 default VU가 거의 동시에 시작 → 이 시각을 t0으로 사용
  return { startMs: Date.now(), strategy: STRATEGY };
}

export default function (data) {
  const user = USERS[(__VU - 1) % USERS.length];
  const base = baseForVu(__VU);
  const startMs = data.startMs || Date.now();

  if (!login(base, user.email)) {
    autoFail.add(1);
    return;
  }

  const payload = JSON.stringify({
    userId: user.id,
    lat: LAT,
    lng: LNG,
    maxDistanceKm: MAX_KM,
  });

  const res = http.post(`${base}/api/reservations/auto`, payload, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "auto_reserve" },
  });

  autoDuration.add(res.timings.duration);

  if (res.status === 201) {
    autoOk.add(1);
    okElapsedFromStart.add(Date.now() - startMs);
  } else if (res.status === 409) {
    autoConflict.add(1);
  } else {
    autoFail.add(1);
    console.log(
      `VU=${__VU} userId=${user.id} status=${res.status} body=${String(res.body).slice(0, 200)}`
    );
  }

  check(res, {
    "got 201 or 409 or 404": (r) =>
      r.status === 201 || r.status === 409 || r.status === 404,
  });
  sleep(0.01);
}

export function handleSummary(data) {
  const m = data.metrics;
  const vals = (name) => (m[name] && m[name].values) || {};
  const ok = vals("auto_reserve_ok").count || 0;
  const elapsed = vals("ok_elapsed_from_start");
  const lastOkMs = elapsed.max != null ? elapsed.max : null;
  const firstOkMs = elapsed.min != null ? elapsed.min : null;

  console.log("---- 50 slots / 50 users summary ----");
  console.log(
    `STRATEGY=${STRATEGY} BASE_URLS=${BASE_URLS.join(",")} MAX_KM=${MAX_KM} users=${USERS.length}`
  );
  console.log(`auto_reserve_ok: ${ok}`);
  console.log(`auto_reserve_conflict: ${vals("auto_reserve_conflict").count || 0}`);
  console.log(`auto_reserve_fail: ${vals("auto_reserve_fail").count || 0}`);
  console.log(
    `auto_reserve_duration: avg=${(vals("auto_reserve_duration").avg || 0).toFixed(1)}ms p95=${(vals("auto_reserve_duration")["p(95)"] || 0).toFixed(1)}ms`
  );
  if (lastOkMs != null) {
    console.log(`first_success_after_start: ${(firstOkMs / 1000).toFixed(3)}s (${firstOkMs.toFixed(0)}ms)`);
    console.log(`LAST_success_after_start: ${(lastOkMs / 1000).toFixed(3)}s (${lastOkMs.toFixed(0)}ms)  ← 마지막 성공(≈50번째)까지`);
    console.log(`spread_first_to_last_ok: ${((lastOkMs - firstOkMs) / 1000).toFixed(3)}s`);
  } else {
    console.log("LAST_success_after_start: n/a (성공 0)");
  }
  return {};
}
