package com.makemytrip.makemytrip.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A user review (1-5 stars, text, photos) of a hotel or a flight. */
@Data
@NoArgsConstructor
@Document(collection = "reviews")
@CompoundIndex(def = "{'targetType': 1, 'targetId': 1}")
public class Review {
    @Id
    private String id;
    private String targetType;     // HOTEL / FLIGHT
    private String targetId;
    private String targetName;
    @Indexed
    private String userId;
    private String userName;
    private boolean verified;      // the author has booked this hotel / flight
    private int rating;
    private String title;
    private String text;
    private List<String> photoIds = new ArrayList<>();
    private List<String> helpfulBy = new ArrayList<>();
    private int helpfulCount;
    private List<Reply> replies = new ArrayList<>();
    private List<Flag> flags = new ArrayList<>();
    private int flagCount;
    private String status = "PUBLISHED";   // PUBLISHED / HIDDEN (many flags, waiting for a moderator) / REMOVED
    private boolean flagsReviewed;         // a moderator has looked at the flags and kept the review
    private String moderationNote;
    private String moderatedBy;
    private Instant moderatedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reply {
        private String id;
        private String userId;
        private String userName;
        private String text;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Flag {
        private String userId;
        private String reason;
        private String details;
        private Instant createdAt;
    }
}
