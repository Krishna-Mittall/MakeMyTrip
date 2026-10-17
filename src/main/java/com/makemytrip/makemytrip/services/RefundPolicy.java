package com.makemytrip.makemytrip.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Predefined refund policy. The rules are checked in this order:
 *
 *   1. Cancelled within 24 hours of the reservation (the booking time)  -> 50% of the booking amount
 *   2. Otherwise the refund depends on how close the cancellation is to the travel (flight departure / hotel check-in):
 *        Flights : 72h or more -> 90%,  24-72h -> 75%,  3-24h -> 50%,  less than 3h -> 0%
 *        Hotels  : 72h or more -> 100%, 24-72h -> 75%,  less than 24h -> 50%
 *
 * Cancelling only some of the seats / rooms gives a partial refund of just that part.
 */
public final class RefundPolicy {
    public static final int RESERVATION_WINDOW_HOURS = 24;

    private RefundPolicy() {
    }

    public static class Result {
        public final int percent;
        public final String text;
        public final String key;

        Result(int percent, String text, String key) {
            this.percent = percent;
            this.text = text;
            this.key = key;
        }
    }

    /**
     * @param hoursToTravel    whole hours left until departure / check-in
     * @param hoursSinceBooking whole hours since the booking was made (use a large number when unknown)
     */
    public static Result evaluate(boolean flight, long hoursToTravel, long hoursSinceBooking) {
        String what = flight ? "departure" : "check-in";
        if (hoursSinceBooking < RESERVATION_WINDOW_HOURS) {
            return new Result(50, "Cancelled within 24 hours of the reservation: 50% refund", "WITHIN_24H");
        }
        if (flight) {
            if (hoursToTravel >= 72) {
                return new Result(90, "Cancelled 72+ hours before " + what + ": 90% refund", "T72");
            }
            if (hoursToTravel >= 24) {
                return new Result(75, "Cancelled 24-72 hours before " + what + ": 75% refund", "T24");
            }
            if (hoursToTravel >= 3) {
                return new Result(50, "Cancelled 3-24 hours before " + what + ": 50% refund", "T3");
            }
            return new Result(0, "Cancelled less than 3 hours before " + what + ": no refund", "T0");
        }
        if (hoursToTravel >= 72) {
            return new Result(100, "Cancelled 72+ hours before " + what + ": full refund", "T72");
        }
        if (hoursToTravel >= 24) {
            return new Result(75, "Cancelled 24-72 hours before " + what + ": 75% refund", "T24");
        }
        return new Result(50, "Cancelled less than 24 hours before " + what + ": 50% refund", "T0");
    }

    /** The policy as a table for the UI, in the order the rules are applied. */
    public static List<Map<String, Object>> table(boolean flight) {
        String what = flight ? "departure" : "check-in";
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("WITHIN_24H", "Cancelled within 24 hours of the reservation (booking time)", 50));
        rows.add(row("T72", "Later: 72 hours or more before " + what, flight ? 90 : 100));
        rows.add(row("T24", "Later: 24 to 72 hours before " + what, 75));
        if (flight) {
            rows.add(row("T3", "Later: 3 to 24 hours before " + what, 50));
            rows.add(row("T0", "Later: less than 3 hours before " + what, 0));
        } else {
            rows.add(row("T0", "Later: less than 24 hours before " + what, 50));
        }
        return rows;
    }

    private static Map<String, Object> row(String key, String when, int percent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("when", when);
        m.put("refundPercent", percent);
        return m;
    }
}
