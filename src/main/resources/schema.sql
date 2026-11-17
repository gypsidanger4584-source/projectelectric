CREATE TABLE IF NOT EXISTS shedlock (
	name VARCHAR(64) NOT NULL,
	lock_until TIMESTAMP NOT NULL,
	locked_at TIMESTAMP NOT NULL,
	locked_by VARCHAR(255) NOT NULL,
	PRIMARY KEY (name)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_charger_one_pending
	ON reservations (charger_id)
	WHERE status = 'PENDING';
