package com.makemytrip.makemytrip;

import com.makemytrip.makemytrip.services.RefundPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundPolicyTest {

    @Test
    void flightRefundsFollowTheTimeLeft() {
        assertEquals(90, RefundPolicy.evaluate(true, 200).percent);
        assertEquals(90, RefundPolicy.evaluate(true, 72).percent);
        assertEquals(75, RefundPolicy.evaluate(true, 71).percent);
        assertEquals(75, RefundPolicy.evaluate(true, 24).percent);
        assertEquals(50, RefundPolicy.evaluate(true, 23).percent);
        assertEquals(50, RefundPolicy.evaluate(true, 3).percent);
        assertEquals(0, RefundPolicy.evaluate(true, 2).percent);
        assertEquals(0, RefundPolicy.evaluate(true, -5).percent);
    }

    @Test
    void hotelRefundsFollowTheTimeLeft() {
        assertEquals(100, RefundPolicy.evaluate(false, 120).percent);
        assertEquals(75, RefundPolicy.evaluate(false, 48).percent);
        assertEquals(50, RefundPolicy.evaluate(false, 10).percent);
        assertEquals(50, RefundPolicy.evaluate(false, 0).percent);
    }

    @Test
    void textExplainsTheRule() {
        assertTrue(RefundPolicy.evaluate(true, 10).text.contains("within 24 hours of the reservation"));
        assertFalse(RefundPolicy.table(true).isEmpty());
        assertFalse(RefundPolicy.table(false).isEmpty());
    }
}
