package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A browser that agreed to receive push notifications (Web Push). */
@Data
@NoArgsConstructor
@Document(collection = "push_subscriptions")
public class PushSubscription {
    @Id
    private String id;
    @Indexed
    private String userId;
    @Indexed(unique = true)
    private String endpoint;
    private String p256dh;
    private String auth;
    private Instant createdAt;
}
