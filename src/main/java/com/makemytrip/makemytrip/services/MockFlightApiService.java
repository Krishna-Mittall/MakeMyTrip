package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.MockFlightOverride;
import com.makemytrip.makemytrip.repositories.MockFlightOverrideRepository;
import com.makemytrip.makemytrip.util.TimeUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A stand-in for a real flight-data provider (like FlightAware or AviationStack). The rest of the
 * application only talks to it through {@link #fetch}, exactly as it would call an external API.
 *
 * The answer is generated from the flight's schedule and the current time: the same flight always gets the
 * same "disruption plan", and the delay is announced a day and a half before departure, grows a little later,
 * then the flight moves through boarding, departed, in air and landed. An admin can override a flight.
 */
@Service
public class MockFlightApiService {
    public static final String[] REASONS = {
            "Air traffic congestion", "Bad weather at the destination", "Late arrival of the incoming aircraft",
            "Technical check of the aircraft", "Crew scheduling", "Runway maintenance"};
    private static final int[] DELAYS = {20, 35, 45, 60, 75, 90, 120};
    private static final String PROVIDER = "MockSky Flight Data API v1";

    @Autowired
    private MockFlightOverrideRepository overrideRepository;

    @Data
    public static class Report {
        private String provider = PROVIDER;
        private String flightId;
        private String flightName;
        private String from;
        private String to;
        private String status;
        private int delayMinutes;
        private int arrivalDelayMinutes;
        private String reason;
        private String gate;
        private String terminal;
        private Instant scheduledDeparture;
        private Instant scheduledArrival;
        private Instant estimatedDeparture;
        private Instant estimatedArrival;
        private Instant asOf;               // the time of the provider for this flight
        private long clockShiftMinutes;      // > 0 when an admin fast-forwarded the flight
    }

    public Map<String, MockFlightOverride> overrides() {
        Map<String, MockFlightOverride> map = new HashMap<>();
        overrideRepository.findAll().forEach(o -> map.put(o.getFlightId(), o));
        return map;
    }

    public Report fetch(Flight flight) {
        return fetch(flight, Instant.now(), overrideRepository.findById(flight.getId()).orElse(null));
    }

    public Report fetch(Flight flight, Instant now, MockFlightOverride override) {
        Instant dep = TimeUtil.parse(flight.getDepartureTime());
        Instant arr = TimeUtil.parse(flight.getArrivalTime());
        if (dep == null) {
            dep = now.plus(Duration.ofDays(30));
        }
        if (arr == null || !arr.isAfter(dep)) {
            arr = dep.plus(Duration.ofHours(2));
        }
        Instant at = override != null ? now.plus(Duration.ofMinutes(override.getShiftMinutes())) : now;

        // the "plan" of this flight - always the same for the same flight and departure
        Random rnd = new Random(flight.getId().hashCode() * 31L + dep.getEpochSecond() / 60);
        int roll = rnd.nextInt(100);
        int plannedDelay = roll < 65 ? 0 : DELAYS[rnd.nextInt(DELAYS.length)];
        String reason = REASONS[rnd.nextInt(REASONS.length)];
        int secondRevision = rnd.nextInt(100) < 30 ? 30 : 0;
        int terminalNo = 1 + rnd.nextInt(3);
        char gateLetter = (char) ('A' + rnd.nextInt(4));
        int gateNo = 1 + rnd.nextInt(24);

        long minutesToDeparture = Duration.between(at, dep).toMinutes();
        int delay = 0;
        if (plannedDelay > 0 && minutesToDeparture <= 36 * 60) {
            delay = plannedDelay;
        }
        if (plannedDelay > 0 && secondRevision > 0 && minutesToDeparture <= 4 * 60) {
            delay += secondRevision;
        }
        boolean cancelled = false;
        if (override != null) {
            cancelled = override.isCancelled();
            if (override.getDelayMinutes() != null) {
                delay = Math.max(override.getDelayMinutes(), 0);
                reason = override.getReason() != null && !override.getReason().isBlank() ? override.getReason() : reason;
            }
        }
        int arrivalDelay = Math.max(0, delay - Math.min(15, delay / 3));   // the crew makes up a little time in the air
        Instant estDep = dep.plus(Duration.ofMinutes(delay));
        Instant estArr = arr.plus(Duration.ofMinutes(arrivalDelay));

        String status;
        if (cancelled) {
            status = "CANCELLED";
        } else if (!at.isBefore(estArr)) {
            status = "LANDED";
        } else if (!at.isBefore(estDep.plus(Duration.ofMinutes(15)))) {
            status = "IN_AIR";
        } else if (!at.isBefore(estDep)) {
            status = "DEPARTED";
        } else if (!at.isBefore(estDep.minus(Duration.ofMinutes(45)))) {
            status = "BOARDING";
        } else if (delay > 0) {
            status = "DELAYED";
        } else {
            status = "ON_TIME";
        }

        Report r = new Report();
        r.setFlightId(flight.getId());
        r.setFlightName(flight.getFlightName());
        r.setFrom(flight.getFrom());
        r.setTo(flight.getTo());
        r.setStatus(status);
        r.setDelayMinutes(cancelled ? 0 : delay);
        r.setArrivalDelayMinutes(cancelled ? 0 : arrivalDelay);
        String finalReason = null;
        if (cancelled) {
            finalReason = override.getReason() != null && !override.getReason().isBlank() ? override.getReason() : "Cancelled by the airline";
        } else if (delay > 0) {
            finalReason = reason;
        }
        r.setReason(finalReason);
        if (minutesToDeparture <= 24 * 60) {
            r.setTerminal("T" + terminalNo);
            r.setGate("" + gateLetter + gateNo);
        }
        r.setScheduledDeparture(dep);
        r.setScheduledArrival(arr);
        r.setEstimatedDeparture(estDep);
        r.setEstimatedArrival(estArr);
        r.setAsOf(at);
        r.setClockShiftMinutes(override == null ? 0 : override.getShiftMinutes());
        return r;
    }

    // ------------------------------------------------------------------ admin overrides

    public MockFlightOverride simulate(String flightId, String action, Integer minutes, String reason) {
        MockFlightOverride o = overrideRepository.findById(flightId).orElseGet(() -> {
            MockFlightOverride n = new MockFlightOverride();
            n.setFlightId(flightId);
            return n;
        });
        switch (action.toUpperCase()) {
            case "DELAY" -> {
                o.setCancelled(false);
                o.setDelayMinutes(minutes == null ? 60 : minutes);
                o.setReason(reason);
            }
            case "ON_TIME" -> {
                o.setCancelled(false);
                o.setDelayMinutes(0);
                o.setReason(null);
            }
            case "CANCEL" -> {
                o.setCancelled(true);
                o.setReason(reason);
            }
            case "SHIFT" -> o.setShiftMinutes(o.getShiftMinutes() + (minutes == null ? 60 : minutes));
            case "CLEAR" -> {
                overrideRepository.deleteById(flightId);
                return null;
            }
            default -> throw new IllegalArgumentException("Unknown action " + action);
        }
        o.setUpdatedAt(Instant.now());
        return overrideRepository.save(o);
    }

    /** Demo changes do not last for ever: after a while the flight goes back to its automatic schedule. */
    public int expireOlderThan(Duration age) {
        Instant limit = Instant.now().minus(age);
        int n = 0;
        for (MockFlightOverride o : overrideRepository.findAll()) {
            if (o.getUpdatedAt() == null || o.getUpdatedAt().isBefore(limit)) {
                overrideRepository.deleteById(o.getFlightId());
                n++;
            }
        }
        return n;
    }

    public void clear(String flightId) {
        overrideRepository.deleteById(flightId);
    }
}
