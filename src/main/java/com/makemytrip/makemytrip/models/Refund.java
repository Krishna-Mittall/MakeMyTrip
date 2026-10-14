package com.makemytrip.makemytrip.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The refund that is created when a booking (or a part of it) is cancelled. */
@Data
@NoArgsConstructor
@Document(collection = "refunds")
public class Refund {
    @Id
    private String id;
    private String refundReference;        // e.g. RFD4K9X2A
    @Indexed
    private String bookingReference;
    @Indexed
    private String userId;
    private String userName;
    private String bookingType;            // Flight / Hotel
    private String title;
    private String subtitle;
    private int quantityCancelled;
    private List<String> cancelledSeats = new ArrayList<>();
    private double originalAmount;         // what was paid for the cancelled seats / rooms
    private int refundPercent;
    private double refundAmount;
    private double deduction;              // cancellation charge
    private String policyApplied;
    private String reason;
    private String comments;
    private String status;                 // PENDING / PROCESSED / COMPLETED
    private Instant createdAt;
    private Instant expectedProcessedBy;
    private Instant expectedCompletedBy;
    private Instant processedAt;
    private Instant completedAt;
    private String timelineNote;           // plain-language timeline shown to the user
    private List<TimelineEntry> timeline = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEntry {
        private String status;
        private Instant at;
        private String note;
    }
}
