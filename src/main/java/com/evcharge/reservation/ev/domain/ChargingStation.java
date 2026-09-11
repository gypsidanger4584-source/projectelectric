package com.evcharge.reservation.ev.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "charging_stations",
		uniqueConstraints = @UniqueConstraint(name = "uk_charging_station_stat_id", columnNames = "stat_id")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingStation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "stat_id", nullable = false, unique = true, length = 40)
	private String statId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String address;

	@Column(name = "addr_detail")
	private String addrDetail;

	private String location;

	@Column(name = "use_time")
	private String useTime;

	@Column(name = "busi_id")
	private String busiId;

	private String bnm;

	@Column(name = "busi_nm")
	private String busiNm;

	@Column(name = "busi_call")
	private String busiCall;

	private String zcode;

	private String zscode;

	private String kind;

	@Column(name = "kind_detail")
	private String kindDetail;

	@Column(name = "parking_free")
	private String parkingFree;

	private String note;

	@Column(name = "limit_yn")
	private String limitYn;

	@Column(name = "limit_detail")
	private String limitDetail;

	private Integer year;

	@Column(name = "floor_num")
	private String floorNum;

	@Column(name = "floor_type")
	private String floorType;

	@Column(nullable = false)
	private Double latitude;

	@Column(nullable = false)
	private Double longitude;

	@Column(name = "last_synced_at")
	private LocalDateTime lastSyncedAt;

	public void applyApiSync(
			String name,
			String address,
			String addrDetail,
			String location,
			String useTime,
			String busiId,
			String bnm,
			String busiNm,
			String busiCall,
			String zcode,
			String zscode,
			String kind,
			String kindDetail,
			String parkingFree,
			String note,
			String limitYn,
			String limitDetail,
			Integer year,
			String floorNum,
			String floorType,
			Double latitude,
			Double longitude,
			LocalDateTime lastSyncedAt
	) {
		this.name = name;
		this.address = address;
		this.addrDetail = addrDetail;
		this.location = location;
		this.useTime = useTime;
		this.busiId = busiId;
		this.bnm = bnm;
		this.busiNm = busiNm;
		this.busiCall = busiCall;
		this.zcode = zcode;
		this.zscode = zscode;
		this.kind = kind;
		this.kindDetail = kindDetail;
		this.parkingFree = parkingFree;
		this.note = note;
		this.limitYn = limitYn;
		this.limitDetail = limitDetail;
		this.year = year;
		this.floorNum = floorNum;
		this.floorType = floorType;
		this.latitude = latitude;
		this.longitude = longitude;
		this.lastSyncedAt = lastSyncedAt;
	}
}
