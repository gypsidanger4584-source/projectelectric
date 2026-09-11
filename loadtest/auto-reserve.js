/**
 * Auto-reserve load test
 *
 * users.json = [{ "id": 11, "email": "loadtest01@ev.local" }, ...]
 *
 * PC PowerShell · ev 폴더:
 *   k6 run -e STRATEGY=REDISSON -e BASE_URLS=http://localhost:8080,http://localhost:8081 -e MAX_KM=0.5 loadtest/auto-reserve.js
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
const MAX_KM = Number(__ENV.MAX_KM || 0.5); // 500m default

const autoOk = new Counter("auto_reserve_ok");
const autoConflict = new Counter("auto_reserve_conflict");
const autoFail = new Counter("auto_reserve_fail");
const autoDuration = new Trend("auto_reserve_duration", true);

export const options = {
  scenarios: {
    spike: {
      executor: "per-vu-iterations",
      vus: USERS.length,
      iterations: 1,
      maxDuration: "5m",
    },
  },
  thresholds: {
    checks: ["rate>0.3"],
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
  if (!csrf) {
    console.log(`csrf missing on ${base}`);
    return false;
  }

  const res = http.post(
    `${base}/login`,
    {
      username: email,
      password: PASSWORD,
      _csrf: csrf,
    },
    {
      redirects: 0,
      tags: { name: "login" },
    }
  );

  return res.status === 302 || res.status === 303;
}

export function setup() {
  console.log(`users=${USERS.length} STRATEGY=${STRATEGY} MAX_KM=${MAX_KM} bases=${BASE_URLS.join(",")}`);
  for (const base of BASE_URLS) {
    if (!login(base, USERS[0].email)) {
      console.log(`setup login failed for ${base}`);
      continue;
    }
    const res = http.post(
      `${base}/api/admin/lock-strategy`,
      JSON.stringify({ strategy: STRATEGY }),
      {
        headers: { "Content-Type": "application/json" },
        tags: { name: "lock_strategy_setup" },
      }
    );
    console.log(`setup ${base} STRATEGY=${STRATEGY} status=${res.status}`);
  }
  return { strategy: STRATEGY };
}

export default function () {
  const user = USERS[(__VU - 1) % USERS.length];
  const base = baseForVu(__VU);

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
  } else if (res.status === 409) {
    autoConflict.add(1);
  } else {
    autoFail.add(1);
    console.log(
      `VU=${__VU} userId=${user.id} base=${base} status=${res.status} body=${String(res.body).slice(0, 240)}`
    );
  }

  check(res, {
    "auto finished": (r) =>
      r.status === 201 || r.status === 409 || r.status === 404,
  });

  sleep(0.05);
}

export function handleSummary(data) {
  const m = data.metrics;
  const line = (name) => {
    const x = m[name];
    if (!x) return `${name}: 0`;
    if (x.values.count != null) return `${name}: ${x.values.count}`;
    if (x.values.avg != null) {
      return `${name}: avg=${x.values.avg.toFixed(1)}ms p95=${(x.values["p(95)"] || 0).toFixed(1)}ms`;
    }
    return `${name}: ${JSON.stringify(x.values)}`;
  };
  console.log("---- auto-reserve summary ----");
  console.log(
    `STRATEGY=${STRATEGY} BASE_URLS=${BASE_URLS.join(",")} LAT=${LAT} LNG=${LNG} MAX_KM=${MAX_KM} users=${USERS.length}`
  );
  console.log(line("auto_reserve_ok"));
  console.log(line("auto_reserve_conflict"));
  console.log(line("auto_reserve_fail"));
  console.log(line("auto_reserve_duration"));
  console.log(line("http_req_duration"));
  return {};
}
