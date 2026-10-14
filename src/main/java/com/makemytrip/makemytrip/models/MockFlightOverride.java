package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Lets an admin change what the mock flight-data API reports for one flight (used for demos and tests). */
@Data
@NoArgsConstructor
@Document(collection = "mock_flight_overrides")
public class MockFlightOverride {
    @Id
    private String flightId;
    private boolean cancelled;
    private Integer delayMinutes;     // null = use the generated value
    private String reason;
    private long shiftMinutes;        // moves the clock of this flight forward ("fast forward")
    private Instant updatedAt;
}
