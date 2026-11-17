package com.evcharge.reservation.ev.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.evcharge.reservation.ev.service.ReservationService;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

	private final ReservationService reservationService;

	/** 15초마다 만료된 PENDING 예약을 종료한다. 회차당 한 인스턴스만. */
	@Scheduled(fixedDelay = 15_000L)
	@SchedulerLock(name = "expireOverdueReservations", lockAtMostFor = "PT1M", lockAtLeastFor = "PT10S")
	public void expireOverdueReservations() {
		int expired = reservationService.expireOverdueReservations();
		if (expired > 0) {
			log.info("Expired {} overdue reservation(s)", expired);
		}
	}
}
