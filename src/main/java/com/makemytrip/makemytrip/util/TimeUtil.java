package com.makemytrip.makemytrip.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * Small helper so that every service reads "now" in the same time zone (IST)
 * and parses the date strings stored on flights/bookings the same way.
 */
public final class TimeUtil {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private TimeUtil() {
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(IST);
    }

    /** Parses "2026-10-20T08:00", "2026-10-20T08:00:00Z", "2026-10-20" ... Returns null if it can't. */
    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v).atZone(IST).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(v).atTime(14, 0).atZone(IST).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    public static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(IST).toLocalDate();
    }
}
