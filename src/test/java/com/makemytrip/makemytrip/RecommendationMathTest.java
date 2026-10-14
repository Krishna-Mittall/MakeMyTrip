package com.makemytrip.makemytrip;

import com.makemytrip.makemytrip.services.RecommendationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationMathTest {

    @Test
    void cosineSimilarity() {
        Map<String, Double> a = Map.of("beach", 2.0, "luxury", 1.0);
        assertEquals(1.0, RecommendationService.cosine(a, a), 1e-9);
        assertEquals(0.0, RecommendationService.cosine(a, Map.of("mountain", 3.0)), 1e-9);
        assertEquals(0.0, RecommendationService.cosine(a, Map.of()), 1e-9);
        double part = RecommendationService.cosine(a, Map.of("beach", 1.0, "snow", 1.0));
        assertTrue(part > 0 && part < 1);
    }

    @Test
    void oldBehaviourCountsLess() {
        Instant now = Instant.now();
        assertEquals(1.0, RecommendationService.decay(now, now), 1e-6);
        assertEquals(0.5, RecommendationService.decay(now.minus(Duration.ofDays(45)), now), 0.01);
        assertTrue(RecommendationService.decay(now.minus(Duration.ofDays(200)), now) < 0.1);
        assertEquals(1.0, RecommendationService.decay(null, now));
    }
}
