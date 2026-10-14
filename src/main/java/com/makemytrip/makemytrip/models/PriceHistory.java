package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One point of the price history graph. */
@Data
@NoArgsConstructor
@Document(collection = "pricehistory")
@CompoundIndex(def = "{'itemType': 1, 'itemId': 1, 'timestamp': 1}")
public class PriceHistory {
    @Id
    private String id;
    private String itemType;   // FLIGHT / HOTEL
    private String itemId;
    private double price;
    private double basePrice;
    private Instant timestamp;
    private String reason;     // biggest factor at that moment
    private String source;     // live / backfill
}
