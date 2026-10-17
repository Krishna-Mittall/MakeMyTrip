package com.makemytrip.makemytrip;

import com.makemytrip.makemytrip.services.RefundPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundPolicyTest {
    private static final long LONG_AGO = 1000;

    @Test
    void within24HoursOfTheReservationIsFiftyPercent() {
        assertEquals(50, RefundPolicy.evaluate(true, 500, 0).percent);
        assertEquals(50, RefundPolicy.evaluate(true, 500, 23).percent);
        assertEquals(50, RefundPolicy.evaluate(false, 500, 5).percent);
        assertEquals("WITHIN_24H", RefundPolicy.evaluate(false, 500, 5).key);
        assertTrue(RefundPolicy.evaluate(true, 500, 1).text.contains("within 24 hours of the reservation"));
    }

    @Test
    void flightRefundsFollowTheTimeLeftAfterTheFirstDay() {
        assertEquals(90, RefundPolicy.evaluate(true, 200, LONG_AGO).percent);
        assertEquals(90, RefundPolicy.evaluate(true, 72, 24).percent);
        assertEquals(75, RefundPolicy.evaluate(true, 71, LONG_AGO).percent);
        assertEquals(75, RefundPolicy.evaluate(true, 24, LONG_AGO).percent);
        assertEquals(50, RefundPolicy.evaluate(true, 23, LONG_AGO).percent);
        assertEquals(50, RefundPolicy.evaluate(true, 3, LONG_AGO).percent);
        assertEquals(0, RefundPolicy.evaluate(true, 2, LONG_AGO).percent);
        assertEquals(0, RefundPolicy.evaluate(true, -5, LONG_AGO).percent);
    }

    @Test
    void hotelRefundsFollowTheTimeLeftAfterTheFirstDay() {
        assertEquals(100, RefundPolicy.evaluate(false, 120, LONG_AGO).percent);
        assertEquals(75, RefundPolicy.evaluate(false, 48, LONG_AGO).percent);
        assertEquals(50, RefundPolicy.evaluate(false, 10, LONG_AGO).percent);
        assertEquals(50, RefundPolicy.evaluate(false, 0, LONG_AGO).percent);
    }

    @Test
    void tableListsTheReservationRuleFirst() {
        assertEquals("WITHIN_24H", RefundPolicy.table(true).get(0).get("key"));
        assertEquals("WITHIN_24H", RefundPolicy.table(false).get(0).get("key"));
        assertEquals(5, RefundPolicy.table(true).size());
        assertEquals(4, RefundPolicy.table(false).size());
        assertFalse(RefundPolicy.table(true).isEmpty());
    }
}
