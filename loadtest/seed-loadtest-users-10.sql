-- Load test users (10) + vehicles
-- Login form username field = email (CustomUserDetailsService)
-- Password for all: test1234
-- brand/model → vehicle_connector_mapping (charger types at auto-reserve)

BEGIN;

-- 1) users
INSERT INTO users (email, password, username, created_at, updated_at)
SELECT v.email, v.password, v.username, NOW(), NOW()
FROM (VALUES
	('loadtest01@ev.local', 'loadtest01', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest02@ev.local', 'loadtest02', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest03@ev.local', 'loadtest03', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest04@ev.local', 'loadtest04', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest05@ev.local', 'loadtest05', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest06@ev.local', 'loadtest06', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest07@ev.local', 'loadtest07', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest08@ev.local', 'loadtest08', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest09@ev.local', 'loadtest09', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O'),
	('loadtest10@ev.local', 'loadtest10', '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O')
) AS v(email, username, password)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

-- keep password in sync if users already existed
UPDATE users
SET password = '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O',
    updated_at = NOW()
WHERE email LIKE 'loadtest%@ev.local';

-- 2) vehicles (skip if already present)
INSERT INTO vehicles (
	user_id, brand, model, battery_capacity_kwh, battery_percent,
	range_km, today_distance_km, efficiency, is_charging, updated_at
)
SELECT
	u.id,
	s.brand,
	s.model,
	s.battery_kwh,
	50,
	(s.battery_kwh * 0.5 * 5.0)::int,
	10.0,
	5.0,
	false,
	NOW()
FROM (VALUES
	('loadtest01@ev.local', '현대', '아이오닉5', 77.4),
	('loadtest02@ev.local', '기아', 'EV6', 77.4),
	('loadtest03@ev.local', '테슬라', 'Model3', 57.5),
	('loadtest04@ev.local', '현대', '아이오닉6', 77.4),
	('loadtest05@ev.local', '기아', 'EV9', 99.8),
	('loadtest06@ev.local', '테슬라', 'ModelY', 75.0),
	('loadtest07@ev.local', '쉐보레', '볼트EV', 65.0),
	('loadtest08@ev.local', '기아', '니로EV', 64.8),
	('loadtest09@ev.local', '현대', '코나EV', 64.8),
	('loadtest10@ev.local', '포르쉐', '타이칸', 93.4)
) AS s(email, brand, model, battery_kwh)
JOIN users u ON u.email = s.email
WHERE NOT EXISTS (SELECT 1 FROM vehicles v WHERE v.user_id = u.id);

COMMIT;

SELECT u.id AS user_id, u.email, u.username, v.brand, v.model
FROM users u
JOIN vehicles v ON v.user_id = u.id
WHERE u.email LIKE 'loadtest%@ev.local'
ORDER BY u.email;
