package com.makemytrip.makemytrip;

import com.makemytrip.makemytrip.services.PricingRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PricingRulesTest {

    @Test
    void holidaysAddTwentyPercentWindow() {
        assertEquals(20, PricingRules.HOLIDAY_PERCENT);
        assertEquals("Diwali", PricingRules.holidayName(LocalDate.of(2026, 11, 8)));
        assertEquals("Diwali", PricingRules.holidayName(LocalDate.of(2026, 11, 10)));
        assertNull(PricingRules.holidayName(LocalDate.of(2026, 11, 20)));
    }

    @Test
    void peakSeasonsAreDetected() {
        assertEquals("Summer vacation", PricingRules.peakSeasonName(LocalDate.of(2027, 6, 1)));
        assertEquals("Year-end holidays", PricingRules.peakSeasonName(LocalDate.of(2026, 12, 28)));
        assertEquals("Year-end holidays", PricingRules.peakSeasonName(LocalDate.of(2027, 1, 3)));
        assertNull(PricingRules.peakSeasonName(LocalDate.of(2027, 3, 3)));
    }

    @Test
    void demandGrowsWithOccupancy() {
        assertEquals(0, PricingRules.demandPercent(0, 0));
        assertTrue(PricingRules.demandPercent(0.9, 0) > PricingRules.demandPercent(0.4, 0));
        assertEquals(28, PricingRules.demandPercent(1, 0));
        assertEquals(28, PricingRules.demandPercent(5, 0)); // clamped
        assertEquals(3, PricingRules.demandPercent(0, 3));
    }

    @Test
    void leadTimeRules() {
        assertEquals(20, PricingRules.flightLeadPercent(5));
        assertEquals(12, PricingRules.flightLeadPercent(48));
        assertEquals(6, PricingRules.flightLeadPercent(100));
        assertEquals(0, PricingRules.flightLeadPercent(400));
        assertEquals(-5, PricingRules.flightLeadPercent(24 * 60));
        assertEquals(8, PricingRules.hotelLeadPercent(1));
        assertEquals(-4, PricingRules.hotelLeadPercent(40));
    }

    @Test
    void weekendSurcharge() {
        assertEquals(8, PricingRules.weekendPercent(LocalDate.of(2026, 10, 16), true)); // Friday
        assertEquals(0, PricingRules.weekendPercent(LocalDate.of(2026, 10, 14), true));
        assertEquals(4, PricingRules.weekendPercent(LocalDate.of(2026, 10, 18), false)); // Sunday
    }

    @Test
    void multiplierIsClamped() {
        assertEquals(1.2, PricingRules.multiplierFor(20), 1e-9);
        assertEquals(2.2, PricingRules.multiplierFor(500), 1e-9);
        assertEquals(0.75, PricingRules.multiplierFor(-90), 1e-9);
    }
}
