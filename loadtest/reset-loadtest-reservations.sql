-- Reset loadtest reservations so chargers become free again
BEGIN;

UPDATE chargers c
SET status = 'AVAILABLE'
WHERE c.id IN (
	SELECT r.charger_id
	FROM reservations r
	JOIN users u ON u.id = r.user_id
	WHERE u.email LIKE 'loadtest%@ev.local'
	  AND r.status = 'PENDING'
);

UPDATE reservations r
SET status = 'CANCELLED'
FROM users u
WHERE r.user_id = u.id
  AND u.email LIKE 'loadtest%@ev.local'
  AND r.status = 'PENDING';

COMMIT;

SELECT
	(SELECT count(*) FROM users WHERE email LIKE 'loadtest%') AS loadtest_users,
	(SELECT count(*) FROM reservations r JOIN users u ON u.id = r.user_id WHERE u.email LIKE 'loadtest%' AND r.status = 'PENDING') AS pending_left;
