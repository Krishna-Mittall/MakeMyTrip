package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.FlightStatus;
import com.makemytrip.makemytrip.models.FlightTrack;
import com.makemytrip.makemytrip.models.MockFlightOverride;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.FlightStatusRepository;
import com.makemytrip.makemytrip.repositories.FlightTrackRepository;
import com.makemytrip.makemytrip.util.TimeUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Live flight status. It asks the (mock) flight-data API for the status of the flights, stores what changed
 * and tells everybody who follows the flight - with an in-app notification and a live event.
 */
@Service
public class FlightStatusService {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a").withZone(TimeUtil.IST);
    private static final int MAX_HISTORY = 30;

    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private FlightStatusRepository statusRepository;
    @Autowired
    private FlightTrackRepository trackRepository;
    @Autowired
    private MockFlightApiService mockApi;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private EventHub eventHub;
    @Autowired
    private SeatService seatService;

    @Data
    public static class StatusView {
        private String flightId;
        private String flightName;
        private String from;
        private String to;
        private String status;
        private String label;               // "On Time", "Delayed by 1h" ...
        private String message;             // one sentence for the user
        private int delayMinutes;
        private int arrivalDelayMinutes;
        private String reason;
        private String gate;
        private String terminal;
        private Instant scheduledDeparture;
        private Instant scheduledArrival;
        private Instant estimatedDeparture;
        private Instant estimatedArrival;
        private long minutesToDeparture;
        private long minutesToArrival;
        private int progressPercent;
        private boolean tracked;
        private Instant updatedAt;
        private Instant serverTime;         // the clock of this flight (normally the real time)
        private List<FlightStatus.Change> history = new ArrayList<>();
    }

    // ------------------------------------------------------------------ texts

    public static String durationText(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) {
            return m + "m";
        }
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    public static String labelFor(String status, int delayMinutes) {
        return switch (status) {
            case "DELAYED" -> "Delayed by " + durationText(delayMinutes);
            case "BOARDING" -> "Boarding";
            case "DEPARTED" -> "Departed";
            case "IN_AIR" -> "In the air";
            case "LANDED" -> "Landed";
            case "CANCELLED" -> "Cancelled";
            default -> "On Time";
        };
    }

    private String messageFor(FlightStatus s, Flight f) {
        String route = f.getFrom() + " → " + f.getTo();
        String dep = CLOCK.format(s.getEstimatedDeparture());
        String arr = CLOCK.format(s.getEstimatedArrival());
        String gate = s.getGate() == null ? "" : " Gate " + s.getGate() + ", Terminal " + s.getTerminal().replace("T", "") + ".";
        return switch (s.getStatus()) {
            case "DELAYED" -> "Delayed by " + durationText(s.getDelayMinutes()) + " (" + s.getReason() + "). New departure " + dep + ", arrival " + arr + ".";
            case "BOARDING" -> "Boarding now for " + route + "." + gate + (s.getDelayMinutes() > 0 ? " Departure " + dep + "." : "");
            case "DEPARTED" -> "The flight has left the gate and is taking off.";
            case "IN_AIR" -> "In the air. Expected to land at " + arr + ".";
            case "LANDED" -> "Landed at " + f.getTo() + " (" + arr + ").";
            case "CANCELLED" -> "This flight has been cancelled" + (s.getReason() == null ? "." : " (" + s.getReason() + ").");
            default -> "On time. Departs " + dep + ", arrives " + arr + "." + gate;
        };
    }

    // ------------------------------------------------------------------ sync with the provider

    /** Reads the provider, stores the new state and notifies the followers when something important changed. */
    public synchronized FlightStatus sync(Flight flight) {
        MockFlightApiService.Report r = mockApi.fetch(flight);
        return apply(flight, r);
    }

    private FlightStatus apply(Flight flight, MockFlightApiService.Report r) {
        FlightStatus old = statusRepository.findById(flight.getId()).orElse(null);
        boolean first = old == null;
        FlightStatus s = first ? new FlightStatus() : old;
        boolean changed = first
                || !Objects.equals(s.getStatus(), r.getStatus())
                || s.getDelayMinutes() != r.getDelayMinutes()
                || !Objects.equals(s.getGate(), r.getGate())
                || !Objects.equals(s.getReason(), r.getReason())
                || s.getClockShiftMinutes() != r.getClockShiftMinutes();
        if (!changed) {
            return s;
        }
        String previousStatus = first ? null : s.getStatus();
        int previousDelay = first ? 0 : s.getDelayMinutes();
        Instant previousDeparture = first ? null : s.getEstimatedDeparture();

        s.setFlightId(flight.getId());
        s.setStatus(r.getStatus());
        s.setDelayMinutes(r.getDelayMinutes());
        s.setArrivalDelayMinutes(r.getArrivalDelayMinutes());
        s.setReason(r.getReason());
        s.setGate(r.getGate());
        s.setTerminal(r.getTerminal());
        s.setScheduledDeparture(r.getScheduledDeparture());
        s.setScheduledArrival(r.getScheduledArrival());
        s.setEstimatedDeparture(r.getEstimatedDeparture());
        s.setEstimatedArrival(r.getEstimatedArrival());
        s.setClockShiftMinutes(r.getClockShiftMinutes());
        s.setUpdatedAt(Instant.now());

        boolean statusOrDelayChanged = first || !Objects.equals(previousStatus, r.getStatus()) || previousDelay != r.getDelayMinutes();
        String message = messageFor(s, flight);
        if (statusOrDelayChanged) {
            s.getHistory().add(new FlightStatus.Change(Instant.now(), s.getStatus(), s.getDelayMinutes(), first ? "Status first reported: " + message : message));
            if (s.getHistory().size() > MAX_HISTORY) {
                s.setHistory(new ArrayList<>(s.getHistory().subList(s.getHistory().size() - MAX_HISTORY, s.getHistory().size())));
            }
        }
        statusRepository.save(s);

        if (statusOrDelayChanged) {
            StatusView view = toView(s, flight, null);
            eventHub.publish("FLIGHT_STATUS", view);
            boolean statusChanged = !Objects.equals(previousStatus, r.getStatus());
            boolean worthTelling = statusChanged || "DELAYED".equals(r.getStatus()) || "ON_TIME".equals(r.getStatus());
            if (!first && worthTelling) {
                notifyFollowers(flight, s, previousStatus, previousDelay, previousDeparture, message);
            }
        }
        return s;
    }

    private void notifyFollowers(Flight flight, FlightStatus s, String previousStatus, int previousDelay, Instant previousDeparture, String message) {
        List<FlightTrack> followers = trackRepository.findByFlightId(flight.getId());
        if (followers.isEmpty()) {
            return;
        }
        String title;
        switch (s.getStatus()) {
            case "DELAYED" -> title = previousDelay > 0 && "DELAYED".equals(previousStatus)
                    ? flight.getFlightName() + ": delay updated to " + durationText(s.getDelayMinutes())
                    : flight.getFlightName() + " is delayed by " + durationText(s.getDelayMinutes());
            case "BOARDING" -> title = flight.getFlightName() + " is boarding";
            case "DEPARTED" -> title = flight.getFlightName() + " has departed";
            case "IN_AIR" -> title = flight.getFlightName() + " is in the air";
            case "LANDED" -> title = flight.getFlightName() + " has landed";
            case "CANCELLED" -> title = flight.getFlightName() + " is cancelled";
            default -> title = previousDelay > 0 ? flight.getFlightName() + " is back on time" : flight.getFlightName() + " is on time";
        }
        String text = message;
        if ("DELAYED".equals(s.getStatus()) && previousDeparture != null) {
            text = message + " (was " + CLOCK.format(previousDeparture) + ")";
        }
        for (FlightTrack t : followers) {
            notificationService.notifyUser(t.getUserId(), "FLIGHT_STATUS", title, text, "/flight-status?flight=" + flight.getId());
        }
    }

    // ------------------------------------------------------------------ views

    public StatusView toView(FlightStatus s, Flight f, String userId) {
        StatusView v = new StatusView();
        v.setFlightId(f.getId());
        v.setFlightName(f.getFlightName());
        v.setFrom(f.getFrom());
        v.setTo(f.getTo());
        v.setStatus(s.getStatus());
        v.setLabel(labelFor(s.getStatus(), s.getDelayMinutes()));
        v.setMessage(messageFor(s, f));
        v.setDelayMinutes(s.getDelayMinutes());
        v.setArrivalDelayMinutes(s.getArrivalDelayMinutes());
        v.setReason(s.getReason());
        v.setGate(s.getGate());
        v.setTerminal(s.getTerminal());
        v.setScheduledDeparture(s.getScheduledDeparture());
        v.setScheduledArrival(s.getScheduledArrival());
        v.setEstimatedDeparture(s.getEstimatedDeparture());
        v.setEstimatedArrival(s.getEstimatedArrival());
        Instant now = Instant.now().plus(Duration.ofMinutes(s.getClockShiftMinutes()));
        v.setServerTime(now);
        v.setMinutesToDeparture(Duration.between(now, s.getEstimatedDeparture()).toMinutes());
        v.setMinutesToArrival(Duration.between(now, s.getEstimatedArrival()).toMinutes());
        v.setProgressPercent(progress(s, now));
        v.setUpdatedAt(s.getUpdatedAt());
        v.setHistory(new ArrayList<>(s.getHistory()));
        Collections.reverse(v.getHistory());
        if (userId != null) {
            v.setTracked(trackRepository.findByUserIdAndFlightId(userId, f.getId()) != null);
        }
        return v;
    }

    private int progress(FlightStatus s, Instant now) {
        switch (s.getStatus()) {
            case "LANDED":
                return 100;
            case "DEPARTED":
            case "IN_AIR":
                long total = Duration.between(s.getEstimatedDeparture(), s.getEstimatedArrival()).toSeconds();
                long done = Duration.between(s.getEstimatedDeparture(), now).toSeconds();
                return total <= 0 ? 0 : (int) Math.max(1, Math.min(99, done * 100 / total));
            default:
                return 0;
        }
    }

    private Flight flight(String flightId) {
        return flightRepository.findById(flightId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flight not found"));
    }

    /** The status of one flight. The provider is asked again so that the answer is always current. */
    public StatusView get(String flightId, String userId) {
        Flight f = flight(flightId);
        FlightStatus s = sync(f);
        return toView(s, f, userId);
    }

    public List<StatusView> batch(List<String> flightIds, String userId) {
        List<StatusView> out = new ArrayList<>();
        for (String id : flightIds) {
            Optional<Flight> f = flightRepository.findById(id.trim());
            f.ifPresent(flight -> out.add(toView(sync(flight), flight, userId)));
        }
        return out;
    }

    /** The flights a user follows, the ones that are still to come (or in the air) first. */
    public List<StatusView> tracked(String userId) {
        List<StatusView> out = new ArrayList<>();
        for (FlightTrack t : trackRepository.findByUserId(userId)) {
            flightRepository.findById(t.getFlightId()).ifPresent(f -> {
                StatusView v = toView(sync(f), f, userId);
                v.setTracked(true);
                out.add(v);
            });
        }
        out.sort(Comparator.comparing((StatusView v) -> "LANDED".equals(v.getStatus()) || "CANCELLED".equals(v.getStatus()))
                .thenComparing(StatusView::getEstimatedDeparture));
        return out;
    }

    /** The flights leaving within the next hours (and the ones that left a few hours ago) - like an airport board. */
    public List<StatusView> board(int hours, String query, String userId) {
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofHours(3));
        Instant to = now.plus(Duration.ofHours(Math.max(1, Math.min(hours, 24 * 14))));
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Flight> list = flightRepository.findAll().stream()
                .filter(f -> {
                    Instant dep = TimeUtil.parse(f.getDepartureTime());
                    return dep != null && !dep.isBefore(from) && !dep.isAfter(to);
                })
                .filter(f -> q.isEmpty() || (f.getFlightName() + " " + f.getFrom() + " " + f.getTo()).toLowerCase().contains(q))
                .sorted(Comparator.comparing(f -> TimeUtil.parse(f.getDepartureTime())))
                .limit(40)
                .collect(Collectors.toList());
        Set<String> mine = userId == null ? Set.of() : trackRepository.findByUserId(userId).stream().map(FlightTrack::getFlightId).collect(Collectors.toSet());
        List<StatusView> out = new ArrayList<>();
        for (Flight f : list) {
            StatusView v = toView(sync(f), f, null);
            v.setTracked(mine.contains(f.getId()));
            out.add(v);
        }
        return out;
    }

    // ------------------------------------------------------------------ demo

    private static final int[] DEMO_DELAYS = {45, 60, 90, 120};

    /**
     * "Simulate the next live update" for the demo: every call moves the flight one step further - on time, delayed
     * (with a reason), boarding, departed, in the air, landed, and then back to the automatic schedule. The people who
     * follow the flight get the notification, exactly as if the provider had sent the change.
     */
    public StatusView demoAdvance(String flightId, String userId) {
        Flight f = flight(flightId);
        MockFlightApiService.Report r = mockApi.fetch(f);
        // fix the current delay, so that the automatic plan does not change it while the clock is moved
        if (!"CANCELLED".equals(r.getStatus()) && !"LANDED".equals(r.getStatus())) {
            mockApi.simulate(flightId, "DELAY", r.getDelayMinutes(), r.getReason());
        }
        Instant target = null;
        switch (r.getStatus()) {
            case "ON_TIME" -> mockApi.simulate(flightId, "DELAY", DEMO_DELAYS[new Random().nextInt(DEMO_DELAYS.length)],
                    MockFlightApiService.REASONS[new Random().nextInt(MockFlightApiService.REASONS.length)]);
            case "DELAYED" -> target = r.getEstimatedDeparture().minus(Duration.ofMinutes(30));
            case "BOARDING" -> target = r.getEstimatedDeparture().plus(Duration.ofMinutes(3));
            case "DEPARTED" -> target = r.getEstimatedDeparture().plus(Duration.ofMinutes(20));
            case "IN_AIR" -> target = r.getEstimatedArrival().plus(Duration.ofMinutes(2));
            default -> mockApi.simulate(flightId, "CLEAR", null, null);
        }
        if (target != null) {
            long minutes = (long) Math.ceil(Duration.between(r.getAsOf(), target).toSeconds() / 60.0);
            mockApi.simulate(flightId, "SHIFT", (int) Math.max(1, minutes), null);
        }
        return toView(sync(f), f, userId);
    }

    // ------------------------------------------------------------------ following flights

    public StatusView track(String userId, String flightId, String source, String reference) {
        Flight f = flight(flightId);
        FlightTrack existing = trackRepository.findByUserIdAndFlightId(userId, flightId);
        if (existing == null) {
            FlightTrack t = new FlightTrack();
            t.setUserId(userId);
            t.setFlightId(flightId);
            t.setSource(source);
            t.setReference(reference);
            t.setCreatedAt(Instant.now());
            trackRepository.save(t);
        }
        StatusView v = toView(sync(f), f, userId);
        v.setTracked(true);
        return v;
    }

    public void untrack(String userId, String flightId) {
        trackRepository.deleteByUserIdAndFlightId(userId, flightId);
    }

    // ------------------------------------------------------------------ flight list

    public void enrichFlights(List<Flight> flights) {
        Instant now = Instant.now();
        Map<String, MockFlightOverride> overrides = mockApi.overrides();
        for (Flight f : flights) {
            MockFlightApiService.Report r = mockApi.fetch(f, now, overrides.get(f.getId()));
            f.setLiveStatus(r.getStatus());
            f.setDelayMinutes(r.getDelayMinutes());
        }
    }

    // ------------------------------------------------------------------ scheduler

    /** Every 30 seconds: move landed daily flights to their next day and refresh the flights that matter. */
    @Scheduled(fixedRate = 30000, initialDelay = 20000)
    public void tick() {
        try {
            Instant now = Instant.now();
            mockApi.expireOlderThan(Duration.ofHours(3));
            Set<String> followed = trackRepository.findAll().stream().map(FlightTrack::getFlightId).collect(Collectors.toSet());
            for (Flight f : flightRepository.findAll()) {
                if (f.isAutoRoll()) {
                    roll(f, now);
                }
                Instant dep = TimeUtil.parse(f.getDepartureTime());
                if (dep == null) {
                    continue;
                }
                boolean inWindow = dep.isAfter(now.minus(Duration.ofHours(8))) && dep.isBefore(now.plus(Duration.ofHours(48)));
                if (inWindow || followed.contains(f.getId())) {
                    sync(f);
                }
            }
        } catch (Exception e) {
            // the next run will try again
        }
    }

    /** Daily demo flights fly again the next day: the schedule moves one day (or more) forward and the seat map starts fresh. */
    private void roll(Flight f, Instant now) {
        Instant arr = TimeUtil.parse(f.getArrivalTime());
        Instant dep = TimeUtil.parse(f.getDepartureTime());
        MockFlightOverride o = mockApi.overrides().get(f.getId());
        Instant effective = o == null ? now : now.plus(Duration.ofMinutes(o.getShiftMinutes()));
        if (arr == null || dep == null || arr.plus(Duration.ofHours(1)).isAfter(effective)) {
            return;
        }
        long days = Math.max(1, Duration.between(dep, effective).toDays() + 1);
        int capacity = seatService.capacityOf(f);
        f.setDepartureTime(dep.plus(Duration.ofDays(days)).atZone(TimeUtil.IST).toLocalDateTime().toString());
        f.setArrivalTime(arr.plus(Duration.ofDays(days)).atZone(TimeUtil.IST).toLocalDateTime().toString());
        f.setAvailableSeats((int) Math.round(capacity * (0.40 + 0.50 * new Random().nextDouble())));
        flightRepository.save(f);
        seatService.reset(f.getId());
        statusRepository.deleteById(f.getId());
        mockApi.clear(f.getId());
        eventHub.publish("SEAT_UPDATE", Map.of("flightId", f.getId()));
    }
}
