package com.ticketnest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seat_venue_row_number",
                        columnNames = {"venue_id", "seat_row", "seat_number"}
                )
        }
)
@Getter
@Setter
public class Seat {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @OneToMany(mappedBy = "seat")
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    @Column(name = "seat_row", nullable = false)
    private String row;

    @Column(name = "seat_number", nullable = false)
    private String number;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
}