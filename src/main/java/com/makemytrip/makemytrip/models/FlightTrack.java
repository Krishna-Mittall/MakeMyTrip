package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A flight a user follows. Booked flights are followed automatically. */
@Data
@NoArgsConstructor
@Document(collection = "flight_tracks")
@CompoundIndex(def = "{'userId': 1, 'flightId': 1}", unique = true)
public class FlightTrack {
    @Id
    private String id;
    private String userId;
    private String flightId;
    private String source;      // BOOKING / MANUAL
    private String reference;   // booking reference when source is BOOKING
    private Instant createdAt;
}
