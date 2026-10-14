package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.SeatMap;
import com.makemytrip.makemytrip.repositories.SeatMapRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seat maps for flights: a 3-3 layout (A-F), premium rows at the front, extra legroom rows,
 * temporary seat holds (5 minutes) and permanent bookings.
 */
@Service
public class SeatService {
    public static final String COLUMNS = "ABCDEF";
    public static final int HOLD_MINUTES = 5;
    public static final int MAX_SEATS_PER_BOOKING = 9;
    public static final double PREMIUM_CHARGE = 1500;
    public static final double LEGROOM_CHARGE = 800;

    @Autowired
    private SeatMapRepository seatMapRepository;
    @Autowired
    private EventHub eventHub;

    private final Map<String, Integer> rowsCache = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatView {
        private String seatNumber;
        private int row;
        private String column;
        private String position;     // WINDOW / MIDDLE / AISLE
        private String tier;         // PREMIUM / EXTRA_LEGROOM / STANDARD
        private double extraCharge;
        private String status;       // AVAILABLE / HELD / HELD_BY_YOU / BOOKED
    }

    @Data
    @NoArgsConstructor
    public static class SeatMapView {
        private String flightId;
        private int rows;
        private List<String> columns = new ArrayList<>();
        private String aisleAfter = "C";
        private int capacity;
        private int availableCount;
        private int holdMinutes = HOLD_MINUTES;
        private Long myHeldUntil;
        private Map<String, Double> tierCharges = new LinkedHashMap<>();
        private List<SeatView> seats = new ArrayList<>();
    }

    // ------------------------------------------------------------------ layout helpers

    public static String tierFor(int row, int rows) {
        if (row <= 3) {
            return "PREMIUM";
        }
        int exit = rows / 2 + 2;
        if (row == 4 || row == 5 || row == exit || row == exit + 1) {
            return "EXTRA_LEGROOM";
        }
        return "STANDARD";
    }

    public static double chargeFor(String tier) {
        switch (tier) {
            case "PREMIUM":
                return PREMIUM_CHARGE;
            case "EXTRA_LEGROOM":
                return LEGROOM_CHARGE;
            default:
                return 0;
        }
    }

    public static String positionFor(char column) {
        if (column == 'A' || column == 'F') {
            return "WINDOW";
        }
        if (column == 'C' || column == 'D') {
            return "AISLE";
        }
        return "MIDDLE";
    }

    static int rowsFor(int availableSeats) {
        return Math.max(10, (int) Math.ceil(Math.max(availableSeats, 30) / 6.0));
    }

    private static boolean validSeat(String seat, int rows) {
        if (seat == null || seat.length() < 2) {
            return false;
        }
        char col = seat.charAt(seat.length() - 1);
        if (COLUMNS.indexOf(col) < 0) {
            return false;
        }
        try {
            int row = Integer.parseInt(seat.substring(0, seat.length() - 1));
            return row >= 1 && row <= rows;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ seat map storage

    public synchronized SeatMap ensure(Flight flight) {
        Optional<SeatMap> existing = seatMapRepository.findById(flight.getId());
        if (existing.isPresent()) {
            rowsCache.put(flight.getId(), existing.get().getRows());
            return existing.get();
        }
        int rows = rowsFor(flight.getAvailableSeats());
        int capacity = rows * 6;
        int sold = Math.max(capacity - Math.max(flight.getAvailableSeats(), 0), 0);

        List<String> all = new ArrayList<>();
        for (int r = 1; r <= rows; r++) {
            for (char c : COLUMNS.toCharArray()) {
                all.add(r + "" + c);
            }
        }
        // the seats that were already sold before the booking system started are picked at random,
        // but always the same ones for the same flight
        Collections.shuffle(all, new Random(flight.getId().hashCode()));

        SeatMap map = new SeatMap();
        map.setFlightId(flight.getId());
        map.setRows(rows);
        for (int i = 0; i < sold && i < all.size(); i++) {
            map.getSeats().put(all.get(i), new SeatMap.SeatState("BOOKED", null, 0, "PRE"));
        }
        seatMapRepository.save(map);
        rowsCache.put(flight.getId(), rows);
        return map;
    }

    /** Forgets the seat map of a flight (a daily demo flight that has landed starts again with a fresh map). */
    public synchronized void reset(String flightId) {
        seatMapRepository.deleteById(flightId);
        rowsCache.remove(flightId);
    }

    public int capacityOf(Flight flight) {
        Integer rows = rowsCache.get(flight.getId());
        if (rows == null) {
            rows = ensure(flight).getRows();
        }
        return rows * 6;
    }

    private boolean expire(SeatMap map) {
        long now = System.currentTimeMillis();
        return map.getSeats().entrySet().removeIf(e ->
                "HELD".equals(e.getValue().getStatus()) && e.getValue().getHeldUntil() < now);
    }

    // ------------------------------------------------------------------ view

    public synchronized SeatMapView view(Flight flight, String userId) {
        SeatMap map = ensure(flight);
        if (expire(map)) {
            seatMapRepository.save(map);
        }
        SeatMapView view = new SeatMapView();
        view.setFlightId(flight.getId());
        view.setRows(map.getRows());
        view.setCapacity(map.getRows() * 6);
        for (char c : COLUMNS.toCharArray()) {
            view.getColumns().add(String.valueOf(c));
        }
        view.getTierCharges().put("PREMIUM", PREMIUM_CHARGE);
        view.getTierCharges().put("EXTRA_LEGROOM", LEGROOM_CHARGE);
        view.getTierCharges().put("STANDARD", 0.0);

        int available = 0;
        Long myUntil = null;
        for (int r = 1; r <= map.getRows(); r++) {
            String tier = tierFor(r, map.getRows());
            for (char c : COLUMNS.toCharArray()) {
                String number = r + "" + c;
                SeatMap.SeatState state = map.getSeats().get(number);
                String status = "AVAILABLE";
                if (state != null) {
                    if ("BOOKED".equals(state.getStatus())) {
                        status = "BOOKED";
                    } else if (userId != null && userId.equals(state.getHeldBy())) {
                        status = "HELD_BY_YOU";
                        myUntil = state.getHeldUntil();
                    } else {
                        status = "HELD";
                    }
                }
                if (status.equals("AVAILABLE") || status.equals("HELD_BY_YOU")) {
                    available++;
                }
                view.getSeats().add(new SeatView(number, r, String.valueOf(c), positionFor(c), tier, chargeFor(tier), status));
            }
        }
        view.setAvailableCount(available);
        view.setMyHeldUntil(myUntil);
        return view;
    }

    // ------------------------------------------------------------------ holds

    public synchronized SeatMapView hold(Flight flight, String userId, List<String> requested) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to select seats");
        }
        if (requested.size() > MAX_SEATS_PER_BOOKING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can select at most " + MAX_SEATS_PER_BOOKING + " seats");
        }
        SeatMap map = ensure(flight);
        expire(map);
        // a new selection replaces the previous hold of the same user
        map.getSeats().entrySet().removeIf(e -> "HELD".equals(e.getValue().getStatus()) && userId.equals(e.getValue().getHeldBy()));

        long until = System.currentTimeMillis() + HOLD_MINUTES * 60_000L;
        for (String raw : requested) {
            String seat = raw.trim().toUpperCase();
            if (!validSeat(seat, map.getRows())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid seat " + seat);
            }
            if (map.getSeats().containsKey(seat)) {
                seatMapRepository.save(map);
                throw new ApiException(HttpStatus.CONFLICT, "Seat " + seat + " is no longer available");
            }
            map.getSeats().put(seat, new SeatMap.SeatState("HELD", userId, until, null));
        }
        seatMapRepository.save(map);
        eventHub.publish("SEAT_UPDATE", Map.of("flightId", flight.getId()));
        return view(flight, userId);
    }

    public synchronized SeatMapView releaseHolds(Flight flight, String userId) {
        SeatMap map = ensure(flight);
        boolean changed = map.getSeats().entrySet().removeIf(e ->
                "HELD".equals(e.getValue().getStatus()) && userId.equals(e.getValue().getHeldBy()));
        if (changed) {
            seatMapRepository.save(map);
            eventHub.publish("SEAT_UPDATE", Map.of("flightId", flight.getId()));
        }
        return view(flight, userId);
    }

    // ------------------------------------------------------------------ booking / cancellation

    /** Marks the seats as booked (they must be free or held by this user). Returns the extra seat charges. */
    public synchronized double confirm(Flight flight, List<String> seatNumbers, String userId, String bookingRef) {
        SeatMap map = ensure(flight);
        expire(map);
        double charges = 0;
        for (String raw : seatNumbers) {
            String seat = raw.trim().toUpperCase();
            if (!validSeat(seat, map.getRows())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid seat " + seat);
            }
            SeatMap.SeatState state = map.getSeats().get(seat);
            boolean mine = state != null && "HELD".equals(state.getStatus()) && userId.equals(state.getHeldBy());
            if (state != null && !mine) {
                throw new ApiException(HttpStatus.CONFLICT, "Seat " + seat + " was just taken by someone else. Please choose another seat.");
            }
        }
        for (String raw : seatNumbers) {
            String seat = raw.trim().toUpperCase();
            int row = Integer.parseInt(seat.substring(0, seat.length() - 1));
            charges += chargeFor(tierFor(row, map.getRows()));
            map.getSeats().put(seat, new SeatMap.SeatState("BOOKED", null, 0, bookingRef));
        }
        // drop any other seats this user was still holding on this flight
        map.getSeats().entrySet().removeIf(e -> "HELD".equals(e.getValue().getStatus()) && userId.equals(e.getValue().getHeldBy()));
        seatMapRepository.save(map);
        eventHub.publish("SEAT_UPDATE", Map.of("flightId", flight.getId()));
        return charges;
    }

    /** Picks free standard seats when the passenger did not choose seats (keeps the seat map in line with the inventory). */
    public synchronized List<String> pickSeats(Flight flight, int count, String userId) {
        SeatMap map = ensure(flight);
        expire(map);
        List<String> picked = new ArrayList<>();
        for (int pass = 0; pass < 2 && picked.size() < count; pass++) {
            for (int r = map.getRows(); r >= 1 && picked.size() < count; r--) {
                boolean standard = "STANDARD".equals(tierFor(r, map.getRows()));
                if ((pass == 0) != standard) {
                    continue;   // first pass: standard seats only, second pass: anything that is left
                }
                for (char c : COLUMNS.toCharArray()) {
                    String seat = r + "" + c;
                    SeatMap.SeatState state = map.getSeats().get(seat);
                    boolean free = state == null || ("HELD".equals(state.getStatus()) && userId.equals(state.getHeldBy()));
                    if (free && picked.size() < count) {
                        picked.add(seat);
                    }
                }
            }
        }
        if (picked.size() < count) {
            throw new ApiException(HttpStatus.CONFLICT, "Not enough seats available");
        }
        return picked;
    }

    /** Frees seats again (used when a booking is cancelled). */
    public synchronized void release(String flightId, List<String> seatNumbers) {
        Optional<SeatMap> found = seatMapRepository.findById(flightId);
        if (found.isEmpty() || seatNumbers == null || seatNumbers.isEmpty()) {
            return;
        }
        SeatMap map = found.get();
        for (String raw : seatNumbers) {
            map.getSeats().remove(raw.trim().toUpperCase());
        }
        seatMapRepository.save(map);
        eventHub.publish("SEAT_UPDATE", Map.of("flightId", flightId));
    }

    /** Charge of one seat (used for partial refunds of premium seats). */
    public double chargeOfSeat(String flightId, String seat) {
        Integer rows = rowsCache.get(flightId);
        if (rows == null) {
            rows = seatMapRepository.findById(flightId).map(SeatMap::getRows).orElse(30);
        }
        String s = seat.trim().toUpperCase();
        if (!validSeat(s, rows)) {
            return 0;
        }
        return chargeFor(tierFor(Integer.parseInt(s.substring(0, s.length() - 1)), rows));
    }

    @Scheduled(fixedRate = 30000, initialDelay = 15000)
    public void cleanupExpiredHolds() {
        for (SeatMap map : seatMapRepository.findAll()) {
            if (expire(map)) {
                synchronized (this) {
                    seatMapRepository.save(map);
                }
                eventHub.publish("SEAT_UPDATE", Map.of("flightId", map.getFlightId()));
            }
        }
    }
}
