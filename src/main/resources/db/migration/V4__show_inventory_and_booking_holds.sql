DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM bookings LIMIT 1)
       OR EXISTS (SELECT 1 FROM booking_seats LIMIT 1)
       OR EXISTS (SELECT 1 FROM payments LIMIT 1) THEN
        RAISE EXCEPTION 'V4 cannot migrate non-empty pre-feature booking tables; migrate booking data explicitly first';
    END IF;
END
$$;

CREATE TABLE show_inventories (
    show_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_show_inventories PRIMARY KEY (show_id),
    CONSTRAINT fk_show_inventories_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT ck_show_inventories_currency CHECK (currency ~ '^[A-Z]{3}$')
);

ALTER TABLE bookings DROP CONSTRAINT uk_bookings_idempotency_key;
ALTER TABLE bookings DROP CONSTRAINT ck_bookings_status;
ALTER TABLE bookings
    ADD COLUMN expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ADD COLUMN total_amount NUMERIC(12,2) NOT NULL,
    ADD COLUMN currency VARCHAR(3) NOT NULL,
    ADD CONSTRAINT uk_bookings_user_idempotency UNIQUE (user_id, idempotency_key),
    ADD CONSTRAINT ck_bookings_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'FAILED', 'EXPIRED')),
    ADD CONSTRAINT ck_bookings_total_amount CHECK (total_amount > 0),
    ADD CONSTRAINT ck_bookings_currency CHECK (currency ~ '^[A-Z]{3}$');

DROP TABLE booking_seats;

CREATE TABLE show_seats (
    id UUID NOT NULL,
    show_id UUID NOT NULL,
    source_seat_id UUID NOT NULL,
    seat_row VARCHAR(255) NOT NULL,
    seat_number VARCHAR(255) NOT NULL,
    tier VARCHAR(255) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    status VARCHAR(255) NOT NULL,
    allocated_booking_id UUID,
    hold_expires_at TIMESTAMP(6) WITH TIME ZONE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_show_seats PRIMARY KEY (id),
    CONSTRAINT uk_show_seat_source UNIQUE (show_id, source_seat_id),
    CONSTRAINT fk_show_seats_inventory FOREIGN KEY (show_id) REFERENCES show_inventories (show_id),
    CONSTRAINT fk_show_seats_source FOREIGN KEY (source_seat_id) REFERENCES seats (id),
    CONSTRAINT fk_show_seats_booking FOREIGN KEY (allocated_booking_id) REFERENCES bookings (id),
    CONSTRAINT ck_show_seats_price CHECK (price > 0),
    CONSTRAINT ck_show_seats_status CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    CONSTRAINT ck_show_seats_allocation CHECK (
        (status = 'AVAILABLE' AND allocated_booking_id IS NULL AND hold_expires_at IS NULL)
        OR (status = 'HELD' AND allocated_booking_id IS NOT NULL AND hold_expires_at IS NOT NULL)
        OR (status = 'BOOKED' AND allocated_booking_id IS NOT NULL AND hold_expires_at IS NULL)
    )
);

CREATE INDEX idx_show_seats_show_status ON show_seats (show_id, status);
CREATE INDEX idx_show_seats_expired_holds ON show_seats (hold_expires_at) WHERE status = 'HELD';
CREATE INDEX idx_show_seats_allocated_booking ON show_seats (allocated_booking_id);

CREATE TABLE booking_seats (
    id UUID NOT NULL,
    booking_id UUID NOT NULL,
    show_seat_id UUID NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_booking_seats PRIMARY KEY (id),
    CONSTRAINT uk_booking_show_seat UNIQUE (booking_id, show_seat_id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seats_show_seat FOREIGN KEY (show_seat_id) REFERENCES show_seats (id),
    CONSTRAINT ck_booking_seats_unit_price CHECK (unit_price > 0)
);

CREATE INDEX idx_booking_seats_booking ON booking_seats (booking_id);
CREATE INDEX idx_booking_seats_show_seat ON booking_seats (show_seat_id);
