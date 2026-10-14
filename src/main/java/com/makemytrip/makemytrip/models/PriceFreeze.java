package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A price a user has locked in for a limited period ("price freeze"). */
@Data
@NoArgsConstructor
@Document(collection = "pricefreezes")
public class PriceFreeze {
    @Id
    private String id;
    private String userId;
    private String itemType;     // FLIGHT / HOTEL
    private String itemId;
    private String itemName;
    private double frozenPrice;  // dynamic price per seat / room-night at the moment of freezing
    private String stayDate;     // hotels: the date the price was frozen for
    private int durationHours;
    private Instant createdAt;
    private Instant expiresAt;
    private String status;       // ACTIVE / USED / CANCELLED / EXPIRED
    private String bookingRef;   // set once the frozen price was used
}
