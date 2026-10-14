package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** "Helpful" / "Not relevant" given by a user to a recommended item. */
@Data
@NoArgsConstructor
@Document(collection = "recommendation_feedback")
@CompoundIndex(def = "{'userId': 1, 'itemType': 1, 'itemId': 1}", unique = true)
public class RecommendationFeedback {
    @Id
    private String id;
    private String userId;
    private String itemType;    // HOTEL / FLIGHT
    private String itemId;
    private String value;       // HELPFUL / IRRELEVANT
    private Instant createdAt;
}
