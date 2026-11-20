package com.evcharge.reservation.ev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargerType;
import com.evcharge.reservation.ev.domain.ChargerTypeMapper;
import com.evcharge.reservation.ev.domain.ChargingStation;
import com.evcharge.reservation.ev.domain.Reservation;
import com.evcharge.reservation.ev.domain.ReservationStatus;
import com.evcharge.reservation.ev.domain.ReservationType;
import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.exception.ConflictException;
import com.evcharge.reservation.ev.exception.PendingChargerUnique;
import com.evcharge.reservation.ev.lock.LockStrategyHolder;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ChargingStationRepository;
import com.evcharge.reservation.ev.repository.ReservationRepository;
import com.evcharge.reservation.ev.repository.UserRepository;

/**
 * 실제 PostgreSQL(+Redis) 위에서 돌리는 동시성 통합 테스트.
 * 로컬은 docker compose의 postgres/redis, CI는 서비스 컨테이너를 쓴다.
 * 테스트가 만든 행(유저·충전소·충전기·예약)은 매번 지운다.
 */
@SpringBootTest
class ReservationConcurrencyIntegrationTest {

	private static final int CONCURRENT_USERS = 20;

	@Autowired
	private ReservationService reservationService;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private ChargerRepository chargerRepository;
	@Autowired
	private ChargingStationRepository stationRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private LockStrategyHolder lockStrategyHolder;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PlatformTransactionManager txManager;

	private final List<Long> userIds = new ArrayList<>();
	private final List<Long> chargerIds = new ArrayList<>();
	private final List<Long> stationIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		lockStrategyHolder.set("PESSIMISTIC");
		for (Long chargerId : chargerIds) {
			jdbc.update("delete from reservations where charger_id = ?", chargerId);
		}
		for (Long userId : userIds) {
			jdbc.update("delete from reservations where user_id = ?", userId);
		}
		for (Long chargerId : chargerIds) {
			jdbc.update("delete from chargers where id = ?", chargerId);
		}
		for (Long stationId : stationIds) {
			jdbc.update("delete from charging_stations where id = ?", stationId);
		}
		for (Long userId : userIds) {
			jdbc.update("delete from users where id = ?", userId);
		}
		userIds.clear();
		chargerIds.clear();
		stationIds.clear();
	}

	// ---- 같은 충전기에 동시에 예약 요청: 정확히 1건만 성공해야 한다 ----

	@ParameterizedTest(name = "{0} 락 전략: 동시 {1}건 중 1건만 성공")
	@ValueSource(strings = {"PESSIMISTIC", "REDISSON"})
	void concurrentReserve_exactlyOneSucceeds(String strategy) throws Exception {
		lockStrategyHolder.set(strategy);
		Charger charger = newCharger(ChargerStatus.AVAILABLE);
		List<User> users = new ArrayList<>();
		for (int i = 0; i < CONCURRENT_USERS; i++) {
			users.add(newUser());
		}

		AtomicInteger success = new AtomicInteger();
		AtomicInteger conflict = new AtomicInteger();
		List<Throwable> unexpected = new ArrayList<>();

		runConcurrently(users.stream().<Callable<Void>>map(user -> () -> {
			try {
				reservationService.reserveCharger(
						charger.getId(), user.getId(), ReservationType.MANUAL, null, null);
				success.incrementAndGet();
			} catch (ConflictException ex) {
				conflict.incrementAndGet();
			} catch (ResponseStatusException ex) {
				if (ex.getStatusCode().value() == 409) {
					conflict.incrementAndGet();
				} else {
					synchronized (unexpected) {
						unexpected.add(ex);
					}
				}
			} catch (Throwable ex) {
				synchronized (unexpected) {
					unexpected.add(ex);
				}
			}
			return null;
		}).toList());

		assertThat(unexpected).as("409 외의 예외").isEmpty();
		assertThat(success.get()).as("성공 건수").isEqualTo(1);
		assertThat(conflict.get()).as("409 건수").isEqualTo(CONCURRENT_USERS - 1);
		// HTTP 결과가 아니라 DB 불변조건으로도 확인한다: 충전기당 PENDING은 최대 1건.
		assertThat(countPending(charger.getId())).as("DB의 PENDING 건수").isEqualTo(1);
	}

	// ---- DB 마지막 방어선: partial unique index 자체를 검증 ----

	@Test
	void partialUniqueIndex_blocksSecondPendingForSameCharger() {
		Charger charger = newCharger(ChargerStatus.AVAILABLE);
		User first = newUser();
		User second = newUser();

		reservationRepository.saveAndFlush(newReservation(first, charger, ReservationStatus.PENDING));

		assertThatThrownBy(() ->
				reservationRepository.saveAndFlush(newReservation(second, charger, ReservationStatus.PENDING)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(PendingChargerUnique.isViolation(ex))
						.as("uk_charger_one_pending 위반으로 분류되는가").isTrue());
		assertThat(countPending(charger.getId())).isEqualTo(1);
	}

	@Test
	void partialUniqueIndex_allowsPendingAfterPreviousReservationEnded() {
		Charger charger = newCharger(ChargerStatus.AVAILABLE);
		User first = newUser();
		User second = newUser();

		reservationRepository.saveAndFlush(newReservation(first, charger, ReservationStatus.EXPIRED));
		reservationRepository.saveAndFlush(newReservation(second, charger, ReservationStatus.PENDING));

		// 전체 unique였다면 과거 EXPIRED 행 때문에 재예약이 막혔을 것이다.
		assertThat(countPending(charger.getId())).isEqualTo(1);
	}

	// ---- 취소 vs 만료: 이긴 쪽이 status 조건부 UPDATE로 결정된다 ----

	@Test
	void cancel_whenPending_marksCancelledAndReleasesCharger() {
		Charger charger = newCharger(ChargerStatus.RESERVED);
		User user = newUser();
		Reservation reservation = reservationRepository.saveAndFlush(
				newReservation(user, charger, ReservationStatus.PENDING));

		reservationService.cancel(reservation.getId(), user.getId());

		assertThat(statusOf(reservation.getId())).isEqualTo("CANCELLED");
		assertThat(chargerStatusOf(charger.getId())).isEqualTo("AVAILABLE");
	}

	@Test
	void cancel_afterExpiryWon_returnsConflictAndKeepsExpired() {
		Charger charger = newCharger(ChargerStatus.RESERVED);
		User user = newUser();
		Reservation reservation = reservationRepository.saveAndFlush(
				newReservation(user, charger, ReservationStatus.PENDING));

		// 만료 경로가 먼저 이긴 상황 (PENDING → EXPIRED 조건부 UPDATE 1건)
		int expired = new TransactionTemplate(txManager).execute(status ->
				reservationRepository.expireIfPending(
						reservation.getId(), ReservationStatus.EXPIRED, ReservationStatus.PENDING));
		assertThat(expired).isEqualTo(1);

		assertThatThrownBy(() -> reservationService.cancel(reservation.getId(), user.getId()))
				.isInstanceOf(ConflictException.class);
		assertThat(statusOf(reservation.getId())).as("EXPIRED가 CANCELLED로 덮이면 안 됨").isEqualTo("EXPIRED");
	}

	@Test
	void expire_afterCancelWon_updatesNothing() {
		Charger charger = newCharger(ChargerStatus.RESERVED);
		User user = newUser();
		Reservation reservation = reservationRepository.saveAndFlush(
				newReservation(user, charger, ReservationStatus.PENDING));

		reservationService.cancel(reservation.getId(), user.getId());

		int expired = new TransactionTemplate(txManager).execute(status ->
				reservationRepository.expireIfPending(
						reservation.getId(), ReservationStatus.EXPIRED, ReservationStatus.PENDING));
		assertThat(expired).as("이미 CANCELLED라 0행").isZero();
		assertThat(statusOf(reservation.getId())).isEqualTo("CANCELLED");
	}

	// ---- helpers ----

	private void runConcurrently(List<Callable<Void>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Void>> futures = new ArrayList<>();
			for (Callable<Void> task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (Future<Void> future : futures) {
				future.get(60, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	private User newUser() {
		String key = UUID.randomUUID().toString().substring(0, 12);
		User user = userRepository.save(User.builder()
				.email("it-" + key + "@example.test")
				.password("not-a-real-password")
				.username("it-" + key)
				.build());
		userIds.add(user.getId());
		return user;
	}

	private Charger newCharger(ChargerStatus status) {
		String key = UUID.randomUUID().toString().substring(0, 12);
		ChargingStation station = stationRepository.save(ChargingStation.builder()
				.statId("IT" + key)
				.name("integration-test-station")
				.address("integration-test-address")
				.latitude(37.0)
				.longitude(127.0)
				.build());
		stationIds.add(station.getId());
		Charger charger = chargerRepository.save(Charger.builder()
				.station(station)
				.chgerId("01")
				.chargerType(ChargerType.AC_SLOW)
				.apiChgerTypeCode("02")
				.status(status)
				.statCode(ChargerTypeMapper.toStatCode(status))
				.build());
		chargerIds.add(charger.getId());
		return charger;
	}

	private Reservation newReservation(User user, Charger charger, ReservationStatus status) {
		LocalDateTime now = LocalDateTime.now();
		return Reservation.builder()
				.user(user)
				.charger(charger)
				.startTime(now)
				.endTime(now.plusMinutes(10))
				.holdExpiresAt(now.plusMinutes(10))
				.status(status)
				.type(ReservationType.MANUAL)
				.build();
	}

	private int countPending(Long chargerId) {
		Integer count = jdbc.queryForObject(
				"select count(*) from reservations where charger_id = ? and status = 'PENDING'",
				Integer.class, chargerId);
		return count == null ? 0 : count;
	}

	private String statusOf(Long reservationId) {
		return jdbc.queryForObject("select status from reservations where id = ?", String.class, reservationId);
	}

	private String chargerStatusOf(Long chargerId) {
		return jdbc.queryForObject("select status from chargers where id = ?", String.class, chargerId);
	}
}
