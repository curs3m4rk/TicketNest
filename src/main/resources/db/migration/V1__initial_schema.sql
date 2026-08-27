CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE venues (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_venues PRIMARY KEY (id)
);

CREATE INDEX idx_venues_city_lower ON venues (LOWER(city));

CREATE TABLE shows (
    id UUID NOT NULL,
    venue_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    genre VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_shows PRIMARY KEY (id),
    CONSTRAINT fk_shows_venue FOREIGN KEY (venue_id) REFERENCES venues (id)
);

CREATE INDEX idx_shows_venue_start_time ON shows (venue_id, start_time);
CREATE INDEX idx_shows_start_time ON shows (start_time);
CREATE INDEX idx_shows_genre_lower ON shows (LOWER(genre));

CREATE TABLE seats (
    id UUID NOT NULL,
    venue_id UUID NOT NULL,
    seat_row VARCHAR(255) NOT NULL,
    seat_number VARCHAR(255) NOT NULL,
    tier VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_seats PRIMARY KEY (id),
    CONSTRAINT uk_seat_venue_row_number UNIQUE (venue_id, seat_row, seat_number),
    CONSTRAINT fk_seats_venue FOREIGN KEY (venue_id) REFERENCES venues (id)
);

CREATE INDEX idx_seats_venue_tier ON seats (venue_id, tier);

CREATE TABLE bookings (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    show_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_bookings PRIMARY KEY (id),
    CONSTRAINT uk_bookings_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_bookings_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'FAILED')),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows (id)
);

CREATE INDEX idx_booking_user ON bookings (user_id);
CREATE INDEX idx_booking_show ON bookings (show_id);

CREATE TABLE booking_seats (
    id UUID NOT NULL,
    booking_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_booking_seats PRIMARY KEY (id),
    CONSTRAINT uk_booking_seat UNIQUE (booking_id, seat_id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id)
);

CREATE TABLE payments (
    id UUID NOT NULL,
    booking_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    provider_ref VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_booking UNIQUE (booking_id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

CREATE INDEX idx_payment_booking ON payments (booking_id);

CREATE TABLE notifications (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    sent_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT ck_notifications_type CHECK (
        type IN ('BOOKING_CONFIRMED', 'BOOKING_FAILED', 'PAYMENT_SUCCESS', 'PAYMENT_FAILED')
    ),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_notification_user ON notifications (user_id);

CREATE TABLE refresh_tokens (
    id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_user_revoked ON refresh_tokens (user_id, revoked);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
