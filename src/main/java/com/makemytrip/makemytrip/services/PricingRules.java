package com.makemytrip.makemytrip.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;

/**
 * The rules of the dynamic pricing engine. Every rule returns a percentage that is added to the base price,
 * which keeps the final price easy to explain to the user ("base + 12% demand + 20% Diwali").
 */
public final class PricingRules {
    /** Increase during holidays and peak travel periods. */
    public static final double HOLIDAY_PERCENT = 20;

    private static final Object[][] HOLIDAYS = {
            {"Gandhi Jayanti long weekend", LocalDate.of(2026, 10, 2)},
            {"Dussehra", LocalDate.of(2026, 10, 20)},
            {"Diwali", LocalDate.of(2026, 11, 8)},
            {"Christmas", LocalDate.of(2026, 12, 25)},
            {"New Year", LocalDate.of(2026, 12, 31)},
            {"Republic Day", LocalDate.of(2027, 1, 26)},
            {"Holi", LocalDate.of(2027, 3, 22)},
            {"Independence Day", LocalDate.of(2027, 8, 15)},
            {"Dussehra", LocalDate.of(2027, 10, 9)},
            {"Diwali", LocalDate.of(2027, 10, 29)},
    };

    private PricingRules() {
    }

    public static Object[][] holidays() {
        return HOLIDAYS;
    }

    /** Name of the holiday if the date is within two days of it, otherwise null. */
    public static String holidayName(LocalDate date) {
        for (Object[] h : HOLIDAYS) {
            LocalDate day = (LocalDate) h[1];
            long diff = Math.abs(date.toEpochDay() - day.toEpochDay());
            if (diff <= 2) {
                return (String) h[0];
            }
        }
        return null;
    }

    /** Name of the peak travel season (summer vacation, year end) if the date falls into it, otherwise null. */
    public static String peakSeasonName(LocalDate date) {
        MonthDay md = MonthDay.from(date);
        if (!md.isBefore(MonthDay.of(5, 15)) && !md.isAfter(MonthDay.of(6, 20))) {
            return "Summer vacation";
        }
        if (!md.isBefore(MonthDay.of(12, 20)) || !md.isAfter(MonthDay.of(1, 5))) {
            return "Year-end holidays";
        }
        return null;
    }

    /** More booked seats / rooms (and recent booking activity) means a higher price. */
    public static double demandPercent(double occupancy, double pressurePercent) {
        double occ = Math.max(0, Math.min(1, occupancy));
        return round1(28 * occ * occ + pressurePercent);
    }

    /** Weekend travel is slightly more expensive. */
    public static double weekendPercent(LocalDate date, boolean hotel) {
        DayOfWeek d = date.getDayOfWeek();
        if (hotel) {
            return (d == DayOfWeek.FRIDAY || d == DayOfWeek.SATURDAY) ? 8 : 0;
        }
        return (d == DayOfWeek.FRIDAY || d == DayOfWeek.SUNDAY) ? 4 : 0;
    }

    /** Last-minute flights cost more, very early bookings get a small discount. */
    public static double flightLeadPercent(long hoursToDeparture) {
        if (hoursToDeparture < 0) {
            return 0;
        }
        if (hoursToDeparture < 24) {
            return 20;
        }
        if (hoursToDeparture < 72) {
            return 12;
        }
        if (hoursToDeparture < 168) {
            return 6;
        }
        if (hoursToDeparture > 24 * 45) {
            return -5;
        }
        return 0;
    }

    public static double hotelLeadPercent(long daysToStay) {
        if (daysToStay < 0) {
            return 0;
        }
        if (daysToStay <= 2) {
            return 8;
        }
        if (daysToStay > 30) {
            return -4;
        }
        return 0;
    }

    /** The price never goes below 75% or above 220% of the base price. */
    public static double multiplierFor(double totalPercent) {
        return Math.max(0.75, Math.min(2.2, 1 + totalPercent / 100.0));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
