package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A photo uploaded together with a review (stored in MongoDB, so no extra file storage is needed). */
@Data
@NoArgsConstructor
@Document(collection = "reviewphotos")
public class ReviewPhoto {
    @Id
    private String id;
    private String userId;
    private String contentType;
    private byte[] data;
    private Instant createdAt;
}
