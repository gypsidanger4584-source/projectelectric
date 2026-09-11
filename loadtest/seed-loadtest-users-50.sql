-- Extra loadtest users 11..50 (01..10 already seeded)
-- password: test1234

BEGIN;

INSERT INTO users (email, password, username, created_at, updated_at)
SELECT v.email, v.password, v.username, NOW(), NOW()
FROM (
	SELECT
		'loadtest' || lpad(g::text, 2, '0') || '@ev.local' AS email,
		'loadtest' || lpad(g::text, 2, '0') AS username,
		'$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O' AS password
	FROM generate_series(11, 50) AS g
) v
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

UPDATE users
SET password = '$2a$10$ru8NoiXVZNUGjT9MYvJd8ucgL3p0s97fzuqqtCLoYnc44V55v42/O',
    updated_at = NOW()
WHERE email LIKE 'loadtest%@ev.local';

-- vehicle catalog cycle
WITH catalog(ord, brand, model, battery_kwh) AS (
	VALUES
		(0, '현대', '아이오닉5', 77.4),
		(1, '기아', 'EV6', 77.4),
		(2, '테슬라', 'Model3', 57.5),
		(3, '현대', '아이오닉6', 77.4),
		(4, '기아', 'EV9', 99.8),
		(5, '테슬라', 'ModelY', 75.0),
		(6, '쉐보레', '볼트EV', 65.0),
		(7, '기아', '니로EV', 64.8),
		(8, '현대', '코나EV', 64.8),
		(9, '포르쉐', '타이칸', 93.4)
),
targets AS (
	SELECT
		u.id AS user_id,
		c.brand,
		c.model,
		c.battery_kwh
	FROM users u
	JOIN LATERAL (
		SELECT (substring(u.email from 'loadtest([0-9]+)@'))::int AS n
	) x ON true
	JOIN catalog c ON c.ord = ((x.n - 1) % 10)
	WHERE u.email LIKE 'loadtest%@ev.local'
)
INSERT INTO vehicles (
	user_id, brand, model, battery_capacity_kwh, battery_percent,
	range_km, today_distance_km, efficiency, is_charging, updated_at
)
SELECT
	t.user_id,
	t.brand,
	t.model,
	t.battery_kwh,
	50,
	(t.battery_kwh * 0.5 * 5.0)::int,
	10.0,
	5.0,
	false,
	NOW()
FROM targets t
WHERE NOT EXISTS (SELECT 1 FROM vehicles v WHERE v.user_id = t.user_id);

COMMIT;

SELECT u.id AS user_id, u.email, v.brand, v.model
FROM users u
JOIN vehicles v ON v.user_id = u.id
WHERE u.email LIKE 'loadtest%@ev.local'
ORDER BY u.email;
