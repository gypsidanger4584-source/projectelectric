-- Unify loadtest vehicles + exactly 50 AVAILABLE chargers (02/04) near City Hall
BEGIN;

UPDATE chargers c
SET status = 'AVAILABLE'
WHERE c.id IN (
	SELECT r.charger_id FROM reservations r
	JOIN users u ON u.id = r.user_id
	WHERE u.email LIKE 'loadtest%' AND r.status = 'PENDING'
);
UPDATE reservations r
SET status = 'CANCELLED'
FROM users u
WHERE r.user_id = u.id AND u.email LIKE 'loadtest%' AND r.status = 'PENDING';

UPDATE vehicles v
SET brand = '현대',
    model = '아이오닉5',
    battery_capacity_kwh = 77.4,
    updated_at = NOW()
FROM users u
WHERE v.user_id = u.id AND u.email LIKE 'loadtest%@ev.local';

UPDATE chargers c
SET status = b.prev_status
FROM _loadtest_backup b
WHERE c.id = b.charger_id;
DELETE FROM _loadtest_backup;

CREATE TEMP TABLE _pick50 ON COMMIT DROP AS
SELECT c.id AS charger_id
FROM chargers c
JOIN charging_stations s ON s.id = c.station_id
WHERE (c.del_yn IS NULL OR c.del_yn <> 'Y')
  AND c.api_chger_type_code IN ('02', '04')
  AND s.latitude BETWEEN 37.55 AND 37.58
  AND s.longitude BETWEEN 126.96 AND 127.00
ORDER BY
	(6371 * acos(least(1, greatest(-1,
		cos(radians(37.566826)) * cos(radians(s.latitude)) * cos(radians(s.longitude) - radians(126.9786567))
		+ sin(radians(37.566826)) * sin(radians(s.latitude))
	)))) ASC,
	c.id ASC
LIMIT 50;

-- backup current AVAILABLE in box
INSERT INTO _loadtest_backup (charger_id, prev_status)
SELECT c.id, c.status
FROM chargers c
JOIN charging_stations s ON s.id = c.station_id
WHERE c.status = 'AVAILABLE'
  AND (c.del_yn IS NULL OR c.del_yn <> 'Y')
  AND s.latitude BETWEEN 37.55 AND 37.58
  AND s.longitude BETWEEN 126.96 AND 127.00;

-- break all previously available in box
UPDATE chargers c
SET status = 'BROKEN'
WHERE c.id IN (SELECT charger_id FROM _loadtest_backup);

-- open exactly the 50
UPDATE chargers c
SET status = 'AVAILABLE'
WHERE c.id IN (SELECT charger_id FROM _pick50);

-- keep backup of broken ones for restore + remember pick50 as AVAILABLE baseline
INSERT INTO _loadtest_backup (charger_id, prev_status)
SELECT charger_id, 'AVAILABLE'
FROM _pick50
ON CONFLICT (charger_id) DO UPDATE SET prev_status = EXCLUDED.prev_status;

COMMIT;

SELECT count(*) AS avail_0204_in_box
FROM chargers c
JOIN charging_stations s ON s.id = c.station_id
WHERE c.status = 'AVAILABLE'
  AND c.api_chger_type_code IN ('02', '04')
  AND s.latitude BETWEEN 37.55 AND 37.58
  AND s.longitude BETWEEN 126.96 AND 127.00;
