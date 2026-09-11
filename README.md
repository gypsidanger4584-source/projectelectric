# EV Charge Reservation (`ev`)

전기차 충전소 예약 Spring Boot 애플리케이션입니다.

## 사전 준비

- Java 17+
- Docker (PostgreSQL, Redis)
- Gradle Wrapper (`./gradlew` / `gradlew.bat`)

## 환경 변수 (`.env`)

API 키와 DB 비밀번호는 저장소에 커밋하지 않습니다.

1. `.env.example`을 복사해 `.env`를 만듭니다.
   - Windows: `copy .env.example .env`
   - macOS/Linux: `cp .env.example .env`
2. `.env`에 필요한 값을 채웁니다.
   - `KAKAO_MAPS_API_KEY` — 카카오맵 JavaScript 키
   - `KAKAO_REST_API_KEY` — 카카오 REST / Mobility 키
   - `PUBLIC_DATA_API_KEY` — 공공데이터 충전소 API 키
   - `DB_PASSWORD` — PostgreSQL 비밀번호 (`application.yml` 기본값과 맞출 것)
   - `SESSION_COOKIE_SECURE` — HTTPS 배포 시 `true` (로컬은 `false`)

`application.yml`은 `${KAKAO_MAPS_API_KEY:}`처럼 환경 변수를 참조합니다.  
로컬에서는 `springboot4-dotenv`가 프로젝트 루트의 `.env`를 자동 로드하고, Cursor/VS Code에서는 Run Configuration의 `envFile`로도 로드됩니다.

## 실행 방법

1. DB·Redis 기동 (프로젝트의 Docker Compose 사용):

```bash
docker compose up -d
```

2. 앱 실행 (`ev` 디렉터리에서):

```bash
./gradlew bootRun
```

또는 Cursor에서 **Run and Debug → EvApplication**을 실행합니다. (`.vscode/launch.json`이 `ev/.env`를 지정합니다.)

3. 브라우저에서 `http://localhost:8080` 접속.
