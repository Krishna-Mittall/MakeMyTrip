package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Seat inventory of one flight. Only seats that are BOOKED or HELD are stored,
 * every other seat of the (generated) layout is available.
 */
@Data
@NoArgsConstructor
@Document(collection = "seatmaps")
public class SeatMap {
    @Id
    private String flightId;
    private int rows;
    private Map<String, SeatState> seats = new HashMap<>();

    @Data
    @NoArgsConstructor
    public static class SeatState {
        private String status;      // BOOKED / HELD
        private String heldBy;      // user id (for HELD)
        private long heldUntil;     // epoch millis (for HELD)
        private String bookingRef;  // for BOOKED

        public SeatState(String status, String heldBy, long heldUntil, String bookingRef) {
            this.status = status;
            this.heldBy = heldBy;
            this.heldUntil = heldUntil;
            this.bookingRef = bookingRef;
        }
    }
}
