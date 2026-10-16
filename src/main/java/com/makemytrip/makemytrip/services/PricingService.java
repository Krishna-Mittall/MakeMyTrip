package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.AppSetting;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.PriceFreeze;
import com.makemytrip.makemytrip.models.PriceHistory;
import com.makemytrip.makemytrip.repositories.AppSettingRepository;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.repositories.PriceFreezeRepository;
import com.makemytrip.makemytrip.repositories.PriceHistoryRepository;
import com.makemytrip.makemytrip.util.TimeUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dynamic pricing engine. The price of every flight / hotel is the base price plus a few
 * percentages (demand, holiday / peak season, weekend, last-minute or early-bird). The engine
 * re-calculates the prices every minute, keeps a price history and pushes changes to the browsers.
 */
@Service
public class PricingService {
    public static final String FLIGHT = "FLIGHT";
    public static final String HOTEL = "HOTEL";
    private static final int[] FREEZE_HOURS = {6, 12, 24, 48};
    private static final int MAX_ACTIVE_FREEZES = 3;
    private static final int MAX_HISTORY_POINTS = 1500;

    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private PriceHistoryRepository historyRepository;
    @Autowired
    private AppSettingRepository settingRepository;
    @Autowired
    private PriceFreezeRepository freezeRepository;
    @Autowired
    private SeatService seatService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private EventHub eventHub;

    private final Map<String, Double> pressure = new ConcurrentHashMap<>();      // recent booking activity, in %
    private final Map<String, Double> trendPercent = new ConcurrentHashMap<>();  // change compared with ~1 hour ago
    private final Map<String, Double> lastPublished = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Factor {
        private String code;
        private String label;
        private double percent;
        private String detail;
    }

    /** What is going to change next in the price of an item, so that the price is predictable. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Outlook {
        private long inHours;
        private double deltaPercent;
        private String label;
    }

    @Data
    @NoArgsConstructor
    public static class PriceQuote {
        private String itemType;
        private String itemId;
        private String itemName;
        private double basePrice;
        private double currentPrice;
        private double totalPercent;
        private List<Factor> factors = new ArrayList<>();
        private String trend = "STABLE";
        private double changePercent;
        private String forDate;
        private String computedAt;
        private PriceFreeze freeze;   // the active freeze of the requesting user, if any
        private Outlook outlook;      // the next step in the price because of the booking date (null if none)
    }

    // ------------------------------------------------------------------ quotes

    public PriceQuote quoteFlight(Flight flight) {
        Instant dep = TimeUtil.parse(flight.getDepartureTime());
        ZonedDateTime now = TimeUtil.now();
        LocalDate travelDate = dep != null ? TimeUtil.toLocalDate(dep) : now.toLocalDate();
        int capacity = seatService.capacityOf(flight);
        double occupancy = capacity <= 0 ? 0 : 1.0 - (double) Math.max(flight.getAvailableSeats(), 0) / capacity;
        long hours = dep == null ? 9999 : Duration.between(now.toInstant(), dep).toHours();
        String name = flight.getFlightName() + " (" + flight.getFrom() + " - " + flight.getTo() + ")";
        return build(FLIGHT, flight.getId(), name, flight.getPrice(), occupancy, "seats", travelDate, false, hours);
    }

    public PriceQuote quoteHotel(Hotel hotel, LocalDate stayDate) {
        ZonedDateTime now = TimeUtil.now();
        LocalDate date = stayDate != null ? stayDate : now.toLocalDate();
        int total = roomService.totalRooms(hotel);
        double occupancy = total <= 0 ? 0 : 1.0 - (double) Math.max(hotel.getAvailableRooms(), 0) / total;
        long days = date.toEpochDay() - now.toLocalDate().toEpochDay();
        return build(HOTEL, hotel.getId(), hotel.gethotelName(), hotel.getPricePerNight(), occupancy, "rooms", date, true, days);
    }

    private PriceQuote build(String type, String id, String name, double base, double occupancy, String unit,
                             LocalDate date, boolean hotel, long lead) {
        List<Factor> factors = new ArrayList<>();
        double pressurePct = pressure.getOrDefault(type + ":" + id, 0.0);
        double demand = PricingRules.demandPercent(occupancy, pressurePct);
        String level = occupancy >= 0.75 ? "High demand" : occupancy >= 0.4 ? "Moderate demand" : "Low demand";
        factors.add(new Factor("DEMAND", level, demand, Math.round(occupancy * 100) + "% of " + unit + " booked, plus recent booking activity"));

        String holiday = PricingRules.holidayName(date);
        String peak = PricingRules.peakSeasonName(date);
        if (holiday != null) {
            factors.add(new Factor("HOLIDAY", "Holiday: " + holiday, PricingRules.HOLIDAY_PERCENT, "Festival travel period (+20%)"));
        } else if (peak != null) {
            factors.add(new Factor("PEAK", "Peak season: " + peak, PricingRules.HOLIDAY_PERCENT, "Peak travel period (+20%)"));
        }
        double weekend = PricingRules.weekendPercent(date, hotel);
        if (weekend != 0) {
            factors.add(new Factor("WEEKEND", "Weekend " + (hotel ? "stay" : "travel"), weekend, "Fri-Sun prices are slightly higher"));
        }
        double leadPct = hotel ? PricingRules.hotelLeadPercent(lead) : PricingRules.flightLeadPercent(lead);
        if (leadPct != 0) {
            String label = leadPct > 0 ? "Last-minute booking" : "Early-bird discount";
            String detail = hotel ? (lead + " day(s) until check-in") : (lead < 48 ? lead + " hour(s) until departure" : (lead / 24) + " day(s) until departure");
            factors.add(new Factor("LEAD", label, leadPct, detail));
        }

        double total = factors.stream().mapToDouble(Factor::getPercent).sum();
        PriceQuote quote = new PriceQuote();
        quote.setItemType(type);
        quote.setItemId(id);
        quote.setItemName(name);
        quote.setBasePrice(base);
        quote.setTotalPercent(Math.round(total * 10.0) / 10.0);
        quote.setCurrentPrice(Math.round(base * PricingRules.multiplierFor(total)));
        quote.setFactors(factors);
        quote.setForDate(date.toString());
        quote.setComputedAt(Instant.now().toString());
        quote.setOutlook(outlookFor(hotel, lead));
        Double change = trendPercent.get(type + ":" + id);
        if (change != null) {
            quote.setChangePercent(Math.round(change * 10.0) / 10.0);
            quote.setTrend(change > 0.5 ? "UP" : change < -0.5 ? "DOWN" : "STABLE");
        }
        return quote;
    }

    private static final long[] FLIGHT_STEPS = {24 * 45, 168, 72, 24};   // hours before departure where the lead-time price changes
    private static final long[] HOTEL_STEPS = {30, 2};                    // days before check-in where the lead-time price changes

    /** The next change of the lead-time part of the price ("in 2 days the price goes up by 6%"). */
    static Outlook outlookFor(boolean hotel, long lead) {
        long[] steps = hotel ? HOTEL_STEPS : FLIGHT_STEPS;
        for (long step : steps) {
            if (lead > step) {
                double now = hotel ? PricingRules.hotelLeadPercent(lead) : PricingRules.flightLeadPercent(lead);
                double next = hotel ? PricingRules.hotelLeadPercent(step) : PricingRules.flightLeadPercent(step - 1);
                double delta = next - now;
                if (delta == 0) {
                    continue;
                }
                long hours = hotel ? (lead - step) * 24 : lead - step;
                String label = delta < 0 ? "Price drops"
                        : ((hotel ? step <= 2 : step <= 168) ? "Last-minute pricing starts" : "The early-bird discount ends");
                return new Outlook(Math.max(hours, 1), delta, label);
            }
        }
        return null;
    }

    /** The price of a hotel for the next days (holidays and weekends show up), used for the price calendar. */
    public List<Map<String, Object>> hotelCalendar(String hotelId, int days) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hotel not found"));
        LocalDate today = TimeUtil.now().toLocalDate();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.max(1, Math.min(days, 45)); i++) {
            LocalDate date = today.plusDays(i);
            PriceQuote q = quoteHotel(hotel, date);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("price", q.getCurrentPrice());
            m.put("percent", q.getTotalPercent());
            String tag = null;
            for (Factor f : q.getFactors()) {
                if (f.getCode().equals("HOLIDAY") || f.getCode().equals("PEAK")) {
                    tag = f.getLabel().replaceFirst("^(Holiday|Peak season): ", "");
                    break;
                }
            }
            m.put("tag", tag);
            m.put("weekend", q.getFactors().stream().anyMatch(f -> f.getCode().equals("WEEKEND")));
            out.add(m);
        }
        return out;
    }

    /** A booking makes the item busier: the price reacts at once and the effect fades away in some minutes. */
    public void recordBooking(String type, String id, int units) {
        pressure.merge(type + ":" + id, Math.min(6.0, Math.max(1, units) * 1.5), (a, b) -> Math.min(8.0, a + b));
    }

    public PriceQuote quoteFlightForUser(String flightId, String userId) {
        Flight flight = flightRepository.findById(flightId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flight not found"));
        PriceQuote quote = quoteFlight(flight);
        attachFreeze(quote, userId);
        return quote;
    }

    public PriceQuote quoteHotelForUser(String hotelId, LocalDate stayDate, String userId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hotel not found"));
        PriceQuote quote = quoteHotel(hotel, stayDate);
        attachFreeze(quote, userId);
        return quote;
    }

    private void attachFreeze(PriceQuote quote, String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        for (PriceFreeze f : freezeRepository.findByUserIdAndStatus(userId, "ACTIVE")) {
            if (f.getItemType().equals(quote.getItemType()) && f.getItemId().equals(quote.getItemId()) && f.getExpiresAt().isAfter(now)) {
                quote.setFreeze(f);
            }
        }
    }

    /** Fills the "currentPrice" fields of the flights returned by the normal /flight endpoint. */
    public void enrichFlights(List<Flight> flights) {
        for (Flight f : flights) {
            PriceQuote q = quoteFlight(f);
            f.setCurrentPrice(q.getCurrentPrice());
            f.setPriceTrend(q.getTrend());
            f.setPriceChangePercent(q.getChangePercent());
        }
    }

    public void enrichHotels(List<Hotel> hotels) {
        for (Hotel h : hotels) {
            PriceQuote q = quoteHotel(h, null);
            h.setCurrentPricePerNight(q.getCurrentPrice());
            h.setPriceTrend(q.getTrend());
            h.setPriceChangePercent(q.getChangePercent());
        }
    }

    // ------------------------------------------------------------------ price freeze

    private Instant now() {
        return Instant.now();
    }

    public synchronized PriceFreeze createFreeze(String userId, String type, String itemId, int hours, LocalDate stayDate) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to freeze a price");
        }
        if (Arrays.stream(FREEZE_HOURS).noneMatch(h -> h == hours)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Freeze duration must be 6, 12, 24 or 48 hours");
        }
        List<PriceFreeze> active = freezeRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(f -> f.getExpiresAt().isAfter(now())).collect(Collectors.toList());
        if (active.size() >= MAX_ACTIVE_FREEZES) {
            throw new ApiException(HttpStatus.CONFLICT, "You can keep at most " + MAX_ACTIVE_FREEZES + " price freezes active at a time");
        }
        for (PriceFreeze f : active) {
            if (f.getItemType().equals(type) && f.getItemId().equals(itemId)) {
                throw new ApiException(HttpStatus.CONFLICT, "You already have an active price freeze for this item");
            }
        }
        PriceQuote quote;
        if (FLIGHT.equals(type)) {
            Flight flight = flightRepository.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flight not found"));
            quote = quoteFlight(flight);
        } else if (HOTEL.equals(type)) {
            Hotel hotel = hotelRepository.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hotel not found"));
            quote = quoteHotel(hotel, stayDate);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "type must be FLIGHT or HOTEL");
        }
        PriceFreeze freeze = new PriceFreeze();
        freeze.setUserId(userId);
        freeze.setItemType(type);
        freeze.setItemId(itemId);
        freeze.setItemName(quote.getItemName());
        freeze.setFrozenPrice(quote.getCurrentPrice());
        freeze.setStayDate(HOTEL.equals(type) ? quote.getForDate() : null);
        freeze.setDurationHours(hours);
        freeze.setCreatedAt(now());
        freeze.setExpiresAt(now().plus(Duration.ofHours(hours)));
        freeze.setStatus("ACTIVE");
        return freezeRepository.save(freeze);
    }

    public List<PriceFreeze> freezesOf(String userId) {
        List<PriceFreeze> list = freezeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (PriceFreeze f : list) {
            if ("ACTIVE".equals(f.getStatus()) && f.getExpiresAt().isBefore(now())) {
                f.setStatus("EXPIRED");
                freezeRepository.save(f);
            }
        }
        return list;
    }

    public synchronized PriceFreeze cancelFreeze(String freezeId, String userId) {
        PriceFreeze f = freezeRepository.findById(freezeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Freeze not found"));
        if (!f.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This freeze belongs to another user");
        }
        if ("ACTIVE".equals(f.getStatus())) {
            f.setStatus("CANCELLED");
            freezeRepository.save(f);
        }
        return f;
    }

    /** Returns the freeze if it can be used for this booking, otherwise throws. */
    public PriceFreeze validateFreeze(String freezeId, String userId, String type, String itemId) {
        PriceFreeze f = freezeRepository.findById(freezeId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Price freeze not found"));
        if (!f.getUserId().equals(userId) || !f.getItemType().equals(type) || !f.getItemId().equals(itemId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This price freeze does not belong to this booking");
        }
        if (!"ACTIVE".equals(f.getStatus()) || f.getExpiresAt().isBefore(now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This price freeze has expired");
        }
        return f;
    }

    /** A freeze protects against price rises - if the price has dropped since, the lower price is charged. */
    public static double effectivePrice(PriceFreeze freeze, double currentPrice) {
        if (freeze == null) {
            return currentPrice;
        }
        return Math.min(freeze.getFrozenPrice(), currentPrice);
    }

    public void markFreezeUsed(PriceFreeze freeze, String bookingRef) {
        if (freeze == null) {
            return;
        }
        freeze.setStatus("USED");
        freeze.setBookingRef(bookingRef);
        freezeRepository.save(freeze);
    }

    // ------------------------------------------------------------------ history

    public Map<String, Object> historyView(String type, String itemId, int days) {
        PriceQuote now;
        double occupancy;
        Instant dep = null;
        boolean hotel = HOTEL.equals(type);
        double base;
        if (hotel) {
            Hotel h = hotelRepository.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hotel not found"));
            now = quoteHotel(h, null);
            base = h.getPricePerNight();
            int total = roomService.totalRooms(h);
            occupancy = total <= 0 ? 0 : 1.0 - (double) h.getAvailableRooms() / total;
        } else {
            Flight f = flightRepository.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flight not found"));
            now = quoteFlight(f);
            base = f.getPrice();
            dep = TimeUtil.parse(f.getDepartureTime());
            int capacity = seatService.capacityOf(f);
            occupancy = capacity <= 0 ? 0 : 1.0 - (double) f.getAvailableSeats() / capacity;
        }
        ensureHistory(type, itemId, now, base, occupancy, hotel, dep);

        Instant from = Instant.now().minus(Duration.ofDays(Math.max(1, Math.min(days, 60))));
        List<PriceHistory> list = historyRepository.findByItemTypeAndItemIdAndTimestampAfterOrderByTimestampAsc(type, itemId, from);
        List<Map<String, Object>> points = new ArrayList<>();
        double min = Double.MAX_VALUE, max = 0, sum = 0;
        PriceHistory lowest = null;
        for (PriceHistory p : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", p.getTimestamp().toString());
            m.put("price", p.getPrice());
            m.put("reason", p.getReason());
            points.add(m);
            min = Math.min(min, p.getPrice());
            max = Math.max(max, p.getPrice());
            sum += p.getPrice();
            if (lowest == null || p.getPrice() < lowest.getPrice()) {
                lowest = p;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemType", type);
        out.put("itemId", itemId);
        out.put("itemName", now.getItemName());
        out.put("basePrice", now.getBasePrice());
        out.put("currentPrice", now.getCurrentPrice());
        out.put("min", list.isEmpty() ? now.getCurrentPrice() : min);
        out.put("max", list.isEmpty() ? now.getCurrentPrice() : max);
        out.put("average", list.isEmpty() ? now.getCurrentPrice() : Math.round(sum / list.size()));
        out.put("lowestAt", lowest == null ? null : lowest.getTimestamp().toString());
        out.put("trend", now.getTrend());
        out.put("changePercent", now.getChangePercent());
        out.put("points", points);
        return out;
    }

    /**
     * Makes sure that the graph has something to show for 7, 30 and 60 days: daily points for the last 60 days and a point
     * every 6 hours for the last 7 days (so that the 7 day view is more detailed than the 30 and 60 day views).
     * Items that were seen before this was added get the missing points once (an "app_settings" marker remembers it).
     */
    private void ensureHistory(String type, String id, PriceQuote now, double base, double occupancy, boolean hotel, Instant dep) {
        String marker = "price-history-v2:" + type + ":" + id;
        if (settingRepository.existsById(marker)) {
            return;
        }
        boolean fresh = historyRepository.countByItemTypeAndItemId(type, id) == 0;
        List<PriceHistory> points = new ArrayList<>();
        for (int d = 60; d >= 1; d--) {
            if (fresh || d > 30) {          // the older items already have the last 30 days
                points.add(backfillPoint(type, id, base, occupancy, hotel, dep, d * 24));
            }
        }
        for (int k = 28; k >= 1; k--) {
            if (k % 4 != 0) {               // the points at whole days are the daily points
                points.add(backfillPoint(type, id, base, occupancy, hotel, dep, k * 6));
            }
        }
        if (fresh) {
            PriceHistory live = new PriceHistory();
            live.setItemType(type);
            live.setItemId(id);
            live.setBasePrice(base);
            live.setPrice(now.getCurrentPrice());
            live.setTimestamp(Instant.now());
            live.setReason(topReason(now));
            live.setSource("live");
            points.add(live);
        }
        historyRepository.saveAll(points);
        settingRepository.save(new AppSetting(marker, Instant.now().toString()));
    }

    private PriceHistory backfillPoint(String type, String id, double base, double occupancy, boolean hotel, Instant dep, int hoursAgo) {
        ZonedDateTime ts = TimeUtil.now().minusHours(hoursAgo);
        double daysAgo = hoursAgo / 24.0;
        LocalDate date = hotel ? ts.toLocalDate() : (dep != null ? TimeUtil.toLocalDate(dep) : ts.toLocalDate());
        double occ = occupancy * Math.max(0.25, 1 - daysAgo / 40.0);
        double pct = PricingRules.demandPercent(occ, 0);
        if (PricingRules.holidayName(date) != null || PricingRules.peakSeasonName(date) != null) {
            pct += PricingRules.HOLIDAY_PERCENT;
        }
        pct += PricingRules.weekendPercent(date, hotel);
        if (!hotel && dep != null) {
            pct += PricingRules.flightLeadPercent(Duration.between(ts.toInstant(), dep).toHours());
        }
        pct += ((Math.abs((id + "@" + hoursAgo).hashCode()) % 500) / 100.0) - 2.5;   // small movement from point to point
        PriceHistory p = new PriceHistory();
        p.setItemType(type);
        p.setItemId(id);
        p.setBasePrice(base);
        p.setPrice(Math.round(base * PricingRules.multiplierFor(pct)));
        p.setTimestamp(ts.toInstant());
        p.setReason("Earlier price");
        p.setSource("backfill");
        return p;
    }

    private String topReason(PriceQuote q) {
        return q.getFactors().stream().max(Comparator.comparingDouble(f -> Math.abs(f.getPercent())))
                .map(Factor::getLabel).orElse("Base price");
    }

    // ------------------------------------------------------------------ live updates

    /** Runs every minute: moves the demand, stores new history points and pushes changed prices to the browsers. */
    @Scheduled(fixedDelay = 60000, initialDelay = 20000)
    public void tick() {
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Flight f : flightRepository.findAll()) {
            int capacity = seatService.capacityOf(f);
            double occ = capacity <= 0 ? 0 : 1.0 - (double) Math.max(f.getAvailableSeats(), 0) / capacity;
            process(FLIGHT, f.getId(), f.getPrice(), occ, false, TimeUtil.parse(f.getDepartureTime()), () -> quoteFlight(f), changes);
        }
        for (Hotel h : hotelRepository.findAll()) {
            int total = roomService.totalRooms(h);
            double occ = total <= 0 ? 0 : 1.0 - (double) Math.max(h.getAvailableRooms(), 0) / total;
            process(HOTEL, h.getId(), h.getPricePerNight(), occ, true, null, () -> quoteHotel(h, null), changes);
        }
        if (!changes.isEmpty()) {
            eventHub.publish("PRICE_UPDATE", changes);
        }
    }

    private void process(String type, String id, double base, double occupancy, boolean hotel, Instant dep,
                         java.util.function.Supplier<PriceQuote> quoter, List<Map<String, Object>> changes) {
        String key = type + ":" + id;
        double p = pressure.getOrDefault(key, 0.0) * 0.92 + random.nextGaussian() * 1.4;
        pressure.put(key, Math.max(-4, Math.min(8, p)));

        PriceQuote quote = quoter.get();
        ensureHistory(type, id, quote, base, occupancy, hotel, dep);

        PriceHistory last = historyRepository.findFirstByItemTypeAndItemIdOrderByTimestampDesc(type, id);
        boolean moved = last == null || Math.abs(quote.getCurrentPrice() - last.getPrice()) / Math.max(last.getPrice(), 1) >= 0.004;
        boolean old = last != null && last.getTimestamp().isBefore(Instant.now().minus(Duration.ofMinutes(20)));
        if (moved || old) {
            PriceHistory point = new PriceHistory();
            point.setItemType(type);
            point.setItemId(id);
            point.setBasePrice(base);
            point.setPrice(quote.getCurrentPrice());
            point.setTimestamp(Instant.now());
            point.setReason(topReason(quote));
            point.setSource("live");
            historyRepository.save(point);
            prune(type, id);
        }

        PriceHistory reference = historyRepository.findFirstByItemTypeAndItemIdAndTimestampBeforeOrderByTimestampDesc(
                type, id, Instant.now().minus(Duration.ofHours(1)));
        if (reference != null && reference.getPrice() > 0) {
            trendPercent.put(key, (quote.getCurrentPrice() - reference.getPrice()) / reference.getPrice() * 100.0);
        }
        PriceQuote withTrend = quoter.get();
        Double previous = lastPublished.put(key, withTrend.getCurrentPrice());
        if (previous == null || Math.abs(previous - withTrend.getCurrentPrice()) > 0.001) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("type", type);
            change.put("id", id);
            change.put("price", withTrend.getCurrentPrice());
            change.put("trend", withTrend.getTrend());
            change.put("changePercent", withTrend.getChangePercent());
            changes.add(change);
        }
    }

    private void prune(String type, String id) {
        long count = historyRepository.countByItemTypeAndItemId(type, id);
        if (count <= MAX_HISTORY_POINTS) {
            return;
        }
        List<PriceHistory> all = historyRepository.findByItemTypeAndItemIdOrderByTimestampAsc(type, id);
        historyRepository.deleteAll(all.subList(0, (int) (count - MAX_HISTORY_POINTS)));
    }

    /** Called after a booking / cancellation so that the demand change shows up immediately. */
    public void publishPrice(String type, String id) {
        try {
            PriceQuote q;
            if (FLIGHT.equals(type)) {
                q = flightRepository.findById(id).map(this::quoteFlight).orElse(null);
            } else {
                q = hotelRepository.findById(id).map(h -> quoteHotel(h, null)).orElse(null);
            }
            if (q == null) {
                return;
            }
            lastPublished.put(type + ":" + id, q.getCurrentPrice());
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("type", type);
            change.put("id", id);
            change.put("price", q.getCurrentPrice());
            change.put("trend", q.getTrend());
            change.put("changePercent", q.getChangePercent());
            eventHub.publish("PRICE_UPDATE", List.of(change));
        } catch (Exception ignored) {
            // prices are also refreshed by the scheduled job
        }
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void expireFreezes() {
        for (PriceFreeze f : freezeRepository.findByStatus("ACTIVE")) {
            if (f.getExpiresAt().isBefore(now())) {
                f.setStatus("EXPIRED");
                freezeRepository.save(f);
            }
        }
    }

    // ------------------------------------------------------------------ transparency

    /** Upcoming holidays that raise prices, so users can plan their booking. */
    public List<Map<String, Object>> upcomingSurges(int days) {
        LocalDate today = TimeUtil.now().toLocalDate();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] h : PricingRules.holidays()) {
            LocalDate day = (LocalDate) h[1];
            if (day.plusDays(2).isBefore(today) || day.minusDays(2).isAfter(today.plusDays(days))) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", h[0]);
            m.put("from", day.minusDays(2).toString());
            m.put("to", day.plusDays(2).toString());
            m.put("percent", PricingRules.HOLIDAY_PERCENT);
            out.add(m);
        }
        return out;
    }
}
