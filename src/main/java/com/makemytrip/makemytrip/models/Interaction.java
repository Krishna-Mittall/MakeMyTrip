package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Something a traveller did: looked at an item, searched for a destination or booked. Used by the recommender. */
@Data
@NoArgsConstructor
@Document(collection = "interactions")
public class Interaction {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String itemType;    // HOTEL / FLIGHT / DESTINATION
    private String itemId;      // hotel id, flight id or the city name (DESTINATION)
    private String type;        // VIEW / SEARCH / BOOK
    private boolean demo;       // part of the sample data that is used to start the collaborative filtering
    private Instant createdAt;
}
