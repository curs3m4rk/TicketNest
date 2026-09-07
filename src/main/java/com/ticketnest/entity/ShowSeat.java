package com.ticketnest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "show_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_show_seat_source", columnNames = {"show_id", "source_seat_id"}),
        indexes = {
                @Index(name = "idx_show_seats_show_status", columnList = "show_id,status"),
                @Index(name = "idx_show_seats_allocated_booking", columnList = "allocated_booking_id")
        }
)
@Getter
@Setter
public class ShowSeat {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private ShowInventory inventory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_seat_id", nullable = false)
    private Seat sourceSeat;

    @Column(name = "seat_row", nullable = false)
    private String row;

    @Column(name = "seat_number", nullable = false)
    private String number;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowSeatStatus status = ShowSeatStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_booking_id")
    private Booking allocatedBooking;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
