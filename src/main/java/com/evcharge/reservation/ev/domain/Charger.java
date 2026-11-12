package com.evcharge.reservation.ev.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "chargers",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_charger_station_chger",
				columnNames = {"station_id", "chger_id"}
		)
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Charger {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "station_id", nullable = false)
	private ChargingStation station;

	@Column(name = "chger_id", nullable = false, length = 10)
	private String chgerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChargerType chargerType;

	@Column(name = "api_chger_type_code", nullable = false, length = 2)
	private String apiChgerTypeCode;

	private Integer output;

	private String method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChargerStatus status;

	@Column(name = "stat_code", nullable = false)
	private Integer statCode;

	@Column(name = "stat_upd_dt")
	private LocalDateTime statUpdDt;

	@Column(name = "last_tsdt")
	private LocalDateTime lastTsdt;

	@Column(name = "last_tedt")
	private LocalDateTime lastTedt;

	@Column(name = "now_tsdt")
	private LocalDateTime nowTsdt;

	@Column(name = "del_yn", length = 1)
	private String delYn;

	@Column(name = "last_synced_at")
	private LocalDateTime lastSyncedAt;

	/**
	 * @param preserveLocalOccupancy true면 우리 앱 점유(status/statCode)는 공공값으로 덮지 않음
	 */
	public void applyApiSync(
			ChargerType chargerType,
			String apiChgerTypeCode,
			Integer output,
			String method,
			ChargerStatus status,
			Integer statCode,
			LocalDateTime statUpdDt,
			LocalDateTime lastTsdt,
			LocalDateTime lastTedt,
			LocalDateTime nowTsdt,
			String delYn,
			LocalDateTime lastSyncedAt,
			boolean preserveLocalOccupancy
	) {
		this.chargerType = chargerType;
		this.apiChgerTypeCode = apiChgerTypeCode;
		this.output = output;
		this.method = method;
		if (!preserveLocalOccupancy) {
			this.status = status;
			this.statCode = statCode;
		}
		this.statUpdDt = statUpdDt;
		this.lastTsdt = lastTsdt;
		this.lastTedt = lastTedt;
		this.nowTsdt = nowTsdt;
		this.delYn = delYn;
		this.lastSyncedAt = lastSyncedAt;
	}

	/**
	 * 예약/해제 시 도메인 status와 공공 호환 statCode를 함께 맞춘다.
	 * (지도 nearby 집계와 예약 점유가 같은 기준을 쓰도록)
	 */
	public void markStatus(ChargerStatus status) {
		this.status = status;
		this.statCode = ChargerTypeMapper.toStatCode(status);
	}
}
