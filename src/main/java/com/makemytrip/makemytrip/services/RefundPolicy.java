package com.makemytrip.makemytrip.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Predefined refund policy. The refund depends on how close the cancellation is to the reservation
 * (flight departure / hotel check-in):
 *
 *   Flights : 72h or more -> 90%,  24-72h -> 75%,  3-24h -> 50%,  less than 3h -> 0%
 *   Hotels  : 72h or more -> 100%, 24-72h -> 75%,  less than 24h -> 50%
 *
 * Cancelling only some of the seats / rooms gives a partial refund of just that part.
 */
public final class RefundPolicy {
    private RefundPolicy() {
    }

    public static class Result {
        public final int percent;
        public final String text;

        Result(int percent, String text) {
            this.percent = percent;
            this.text = text;
        }
    }

    public static Result evaluate(boolean flight, long hoursToTravel) {
        String what = flight ? "departure" : "check-in";
        if (flight) {
            if (hoursToTravel >= 72) {
                return new Result(90, "Cancelled 72+ hours before " + what + ": 90% refund");
            }
            if (hoursToTravel >= 24) {
                return new Result(75, "Cancelled 24-72 hours before " + what + ": 75% refund");
            }
            if (hoursToTravel >= 3) {
                return new Result(50, "Cancelled within 24 hours of the reservation: 50% refund");
            }
            return new Result(0, "Cancelled less than 3 hours before " + what + ": no refund");
        }
        if (hoursToTravel >= 72) {
            return new Result(100, "Cancelled 72+ hours before " + what + ": full refund");
        }
        if (hoursToTravel >= 24) {
            return new Result(75, "Cancelled 24-72 hours before " + what + ": 75% refund");
        }
        return new Result(50, "Cancelled within 24 hours of the reservation: 50% refund");
    }

    /** The policy as a table for the UI. */
    public static List<Map<String, Object>> table(boolean flight) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("72 hours or more before " + (flight ? "departure" : "check-in"), flight ? 90 : 100));
        rows.add(row("24 to 72 hours before", 75));
        if (flight) {
            rows.add(row("3 to 24 hours before (within 24 hours of the reservation)", 50));
            rows.add(row("Less than 3 hours before", 0));
        } else {
            rows.add(row("Less than 24 hours before (within 24 hours of the reservation)", 50));
        }
        return rows;
    }

    private static Map<String, Object> row(String when, int percent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("when", when);
        m.put("refundPercent", percent);
        return m;
    }
}
