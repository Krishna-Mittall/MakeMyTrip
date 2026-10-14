package com.makemytrip.makemytrip.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The last status we know about a flight (the id is the flight id) and the list of changes. */
@Data
@NoArgsConstructor
@Document(collection = "flight_status")
public class FlightStatus {
    @Id
    private String flightId;
    private String status;            // ON_TIME / DELAYED / BOARDING / DEPARTED / IN_AIR / LANDED / CANCELLED
    private int delayMinutes;
    private int arrivalDelayMinutes;
    private String reason;
    private String gate;
    private String terminal;
    private Instant scheduledDeparture;
    private Instant scheduledArrival;
    private Instant estimatedDeparture;
    private Instant estimatedArrival;
    private long clockShiftMinutes;   // demo only: how far the flight's clock was fast-forwarded
    private Instant updatedAt;
    private List<Change> history = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Change {
        private Instant at;
        private String status;
        private int delayMinutes;
        private String message;
    }
}
