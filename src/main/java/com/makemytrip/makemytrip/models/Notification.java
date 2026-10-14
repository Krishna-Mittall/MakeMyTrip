package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String type;      // REFUND / FLIGHT_STATUS / PRICE / SYSTEM
    private String title;
    private String message;
    private String link;      // page that is opened when the notification is clicked
    private Instant createdAt;
    private boolean read;
}
