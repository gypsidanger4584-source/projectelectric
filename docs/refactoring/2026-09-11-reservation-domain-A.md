# 예약 도메인 리팩토링 기록 (접근 A)

- **일자:** 2026-09-11  
- **범위:** 예약 도메인 — 안전한 정리만 (동작·API·동시성 정책 유지)  
- **목표:** 중복 제거·정책 명시·컨트롤러 검증 정리. 기능 변경 없음.

---

## 1. 전제 조건 (유지한 것)

다음 항목은 **의도적으로 수정하지 않음**.

| 대상 | 이유 |
|------|------|
| `RedissonLockStrategy` TX 동기화 / unlock 순서 | 커밋 전 unlock 시 레이스 |
| `reserveCharger`의 `REQUIRES_NEW` | 자동예약 후보 탐색과 점유 TX 분리 |
| `autoReserve`의 `NOT_SUPPORTED` | 긴 후보 탐색이 DB 커넥션을 오래 잡지 않도록 |
| `findByIdForUpdate` 및 PENDING/AVAILABLE 검사 순서 | 동시성 핵심 |
| `ReservationExpiryScheduler` 주기·만료 의미 | 홀드 UX |
| Kakao 호출 / `loadtest.skip-kakao` | 외부 API·부하테스트 계약 |
| Favorite 속도 규칙 vs Auto 속도 규칙 | 현재 구현이 서로 다름 → 합치면 동작 변경 |
| Favorite 409 폴백 미적용 | Auto만 후보 폴백 — 동작 유지 |

---

## 2. 무엇을 리팩토링했는지 (요약)

1. **직선거리(haversine) 삼중 복제 → `GeoUtils` 공통화**  
2. **주차/장소/외부개방 필터 중복 → `StationPreferenceMatcher` 공통화**  
3. **홀드 시간 상수 → `ReservationHoldPolicy`로 도메인 정책 명시**  
4. **예약 API 컨트롤러 입력 검증 → `ReservationApiSupport`로 통일**  
5. **미사용 `originLat`/`originLng` → 삭제하지 않고 Javadoc으로 문서화**

---

## 3. 상세 변경

### 3.1 `GeoUtils` (신규)

**파일:** `src/main/java/com/evcharge/reservation/ev/util/GeoUtils.java`

**이전**
- `AutoReservationService.haversineKm` (private static)
- `StationMapQueryService.haversineKm` (private static)
- `ChargingStationSyncService.haversineKm` (private static)  
→ 동일 공식이 3곳에 복사되어 있었음.

**이후**
- `GeoUtils.haversineKm(lat1, lng1, lat2, lng2)` 한곳으로 이동.
- 위 3 서비스는 `GeoUtils.haversineKm(...)` 호출로 교체.
- 지구 반경 상수 `6371.0`도 `GeoUtils` 내부로 이동.

**동작**
- 수식·상수 동일 → 반경 필터·정렬 결과 동일.

---

### 3.2 `StationPreferenceMatcher` (신규)

**파일:** `src/main/java/com/evcharge/reservation/ev/util/StationPreferenceMatcher.java`

**이전**
- `AutoReservationService.matchesStationFilters(...)` — soft filter (`false`면 후보 제외)
- `FavoriteService.reserveFromFavorite` 내부 if문 3개 — 조건 불일치 시 **서로 다른 CONFLICT 메시지** throw

**이후**
| 메서드 | 용도 |
|--------|------|
| `matches(...)` | Auto 후보 필터 (기존 soft filter와 동일) |
| `matchesParking(...)` | Favorite — 실패 시 기존 주차 메시지 유지 |
| `matchesPlace(...)` | Favorite — 실패 시 기존 장소 메시지 유지 |
| `matchesExternalOpen(...)` | Favorite — 실패 시 기존 외부개방 메시지 유지 |

**규칙 (기존과 동일)**
- 주차: `parking == FREE` 이면 `parkingFree == Y` 만 통과  
- 장소: `place`가 null/`ALL`이 아니면 `station.kind`와 대소문자 무시 일치  
- 외부개방: `externalOpenOnly`이면 `limitYn == N` 만 통과  

**의도적으로 안 합친 것**
- Auto의 `matchesSpeedFilter` / Favorite의 `matchesPrefer` — 완속·급속 판정이 달라 공통화하면 동작 변경.

---

### 3.3 `ReservationHoldPolicy` (신규)

**파일:** `src/main/java/com/evcharge/reservation/ev/domain/ReservationHoldPolicy.java`

**이전**
- `ReservationService` 내부  
  - `MANUAL_HOLD_MINUTES = 15`  
  - `AUTO_HOLD_MINUTES = 10`  
  - `reserveCharger`에서 직접 `plusMinutes`

**이후**
- `ReservationHoldPolicy.MANUAL_HOLD_MINUTES` / `AUTO_HOLD_MINUTES`
- `holdMinutes(type)` / `holdExpiresAt(now, type)`
- `ReservationService.reserveCharger`는 정책 클래스만 호출.

**동작**
- AUTO → 10분, MANUAL(또는 null) → 15분. 변경 없음.

---

### 3.4 `ReservationApiSupport` (신규)

**파일:** `src/main/java/com/evcharge/reservation/ev/controller/ReservationApiSupport.java`  
(패키지 전용 `package-private`)

**이전**
- `AutoReservationController`: `userId == null` → `"userId는 필수입니다."`
- `ManualReservationController`: FQCN `ResponseStatusException` + `"userId와 chargerId는 필수입니다."`

**이후**
- `ReservationApiSupport.requireUserId`
- `ReservationApiSupport.requireUserIdAndChargerId`
- Auto/Manual 컨트롤러가 동일 헬퍼 사용. HTTP 상태·메시지 동일.

---

### 3.5 `reserveCharger` 좌표 파라미터

**파일:** `ReservationService.java`

**이전:** `originLat`, `originLng`를 받지만 본문에서 미사용 (Auto만 좌표 전달).

**이후:** 시그니처·호출부는 유지. Javadoc에  
「현재 미사용, API 호환·추후 감사 로그용」이라고 명시.  
→ 시그니처 삭제 시 호출 3곳 변경이 생기므로, A안에서는 **문서화만**.

---

## 4. 파일별 변경 목록

### 신규
| 파일 | 역할 |
|------|------|
| `util/GeoUtils.java` | haversine 공용 |
| `util/StationPreferenceMatcher.java` | 주차/장소/외부개방 매칭 |
| `domain/ReservationHoldPolicy.java` | 홀드 분 정책 |
| `controller/ReservationApiSupport.java` | API 입력 검증 |
| `docs/refactoring/2026-09-11-reservation-domain-A.md` | 본 문서 |

### 수정
| 파일 | 변경 요지 |
|------|-----------|
| `service/AutoReservationService.java` | GeoUtils + StationPreferenceMatcher 사용, 로컬 haversine/필터 제거 |
| `service/FavoriteService.java` | StationPreferenceMatcher로 조건 분기 (메시지 유지) |
| `service/ReservationService.java` | ReservationHoldPolicy 사용, origin 파라미터 Javadoc |
| `service/StationMapQueryService.java` | GeoUtils 사용 |
| `service/ChargingStationSyncService.java` | GeoUtils 사용 |
| `controller/AutoReservationController.java` | ReservationApiSupport |
| `controller/ManualReservationController.java` | ReservationApiSupport |

### 미변경 (예약 관련)
- `lock/*` 전 파일  
- `ReservationExpiryScheduler`  
- `ReservationRepository` / 엔티티 상태 전이  
- Favorite 속도 매칭·폴백 동작  

---

## 5. 검증

- `./gradlew compileJava` 성공 (2026-09-11).  
- 권장 수동 확인:
  1. 로그인 후 지도 **자동예약**
  2. 충전기 **수동예약**
  3. 즐겨찾기 예약 (설정 불일치 시 기존 한글 메시지)
  4. 홀드 만료 후 알림 폴링

---

## 6. 다음 후보 (이번 범위 밖 — 접근 B)

- `AutoReservationService`를 후보탐색 / 순위 / 오케스트레이션 클래스로 분할  
- Lock 전략 enum 타입 안전화 (토글 API 문자열 호환 유지)  
- 레거시 `POST /api/reservations` (`create`) deprecate 표시  

동작 변경이 필요한 항목(Favorite 폴백 정렬, 속도 규칙 단일화)은 별도 합의 후 진행.
