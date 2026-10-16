package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.*;
import com.makemytrip.makemytrip.repositories.*;
import com.makemytrip.makemytrip.util.TimeUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Personalised recommendations. Three ideas are combined:
 *  - content based: how well the tags of a hotel / destination (beach, heritage, luxury ...) fit the interests of the user
 *  - collaborative filtering: what travellers with a similar history (cosine similarity) booked or looked at
 *  - popularity and price deals, which also solve the "new user" problem
 * The "helpful" / "not relevant" buttons are fed back into the interest profile.
 */
@Service
public class RecommendationService {
    // how much each kind of behaviour says about the taste of a traveller
    static final double W_BOOK = 5.0, W_VIEW = 1.0, W_SEARCH = 1.5, W_REVIEW_GOOD = 4.0, W_REVIEW_BAD = -2.0;
    static final double W_HELPFUL = 2.0, W_IRRELEVANT = -2.5, W_PREFERENCE = 1.5;
    static final double HALF_LIFE_DAYS = 45;
    static final int NEIGHBOURS = 8;
    private static final Duration IRRELEVANT_FOR = Duration.ofDays(45);

    private static final Map<String, String> PHRASE = Map.ofEntries(
            Map.entry("beach", "beaches"), Map.entry("mountain", "the mountains"), Map.entry("heritage", "heritage stays"),
            Map.entry("city", "city breaks"), Map.entry("nature", "nature escapes"), Map.entry("luxury", "luxury stays"),
            Map.entry("wellness", "wellness retreats"), Map.entry("culture", "culture"), Map.entry("nightlife", "nightlife"),
            Map.entry("lake", "lakeside stays"), Map.entry("budget", "budget stays"), Map.entry("spa", "spa stays"),
            Map.entry("snow", "snow"), Map.entry("business", "business-friendly stays"), Map.entry("sea view", "sea views"));

    @Autowired
    private InteractionRepository interactionRepository;
    @Autowired
    private RecommendationFeedbackRepository feedbackRepository;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PricingService pricingService;
    @Autowired
    private ReviewService reviewService;

    // ------------------------------------------------------------------ output

    @Data
    public static class Item {
        private String key;
        private String type;            // HOTEL / FLIGHT / DESTINATION
        private String itemId;
        private String title;
        private String subtitle;
        private String city;
        private String image;
        private double price;
        private String departureTime;
        private List<String> tags = new ArrayList<>();
        private double rating;
        private int reviewCount;
        private double priceChangePercent;
        private String href;
        private int matchPercent;
        private String headline;
        private List<String> reasons = new ArrayList<>();
        private List<String> algorithms = new ArrayList<>();
        private String feedback;        // HELPFUL if the user already said so
    }

    // ------------------------------------------------------------------ pure maths (unit tested)

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            na += e.getValue() * e.getValue();
            Double other = b.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        for (double v : b.values()) {
            nb += v * v;
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Older behaviour counts less: the weight halves every 45 days. */
    public static double decay(Instant when, Instant now) {
        if (when == null) {
            return 1;
        }
        double days = Math.max(0, Duration.between(when, now).toHours() / 24.0);
        return Math.pow(0.5, days / HALF_LIFE_DAYS);
    }

    static double interactionWeight(String type) {
        return switch (type) {
            case "BOOK" -> W_BOOK;
            case "SEARCH" -> W_SEARCH;
            default -> W_VIEW;
        };
    }

    // ------------------------------------------------------------------ logging behaviour

    public void log(String userId, String itemType, String itemId, String type, String query) {
        if (userId == null || userId.isBlank() || !userRepository.existsById(userId)) {
            return;
        }
        String kind = type == null ? "VIEW" : type.toUpperCase();
        if (!List.of("VIEW", "SEARCH", "BOOK").contains(kind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown interaction type");
        }
        String itemKind = itemType == null ? "" : itemType.toUpperCase();
        String id = itemId;
        if ("SEARCH".equals(kind)) {
            itemKind = "DESTINATION";
            id = knownCity(query);
            if (id == null) {
                return;     // not a destination we know
            }
        } else if (!List.of("HOTEL", "FLIGHT").contains(itemKind) || id == null || id.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown item");
        }
        if ("VIEW".equals(kind) || "SEARCH".equals(kind)) {
            // the same page opened again and again is still one view
            if (interactionRepository.existsByUserIdAndItemTypeAndItemIdAndTypeAndCreatedAtAfter(userId, itemKind, id, kind, Instant.now().minus(Duration.ofMinutes(10)))) {
                return;
            }
        }
        Interaction i = new Interaction();
        i.setUserId(userId);
        i.setItemType(itemKind);
        i.setItemId(id);
        i.setType(kind);
        i.setCreatedAt(Instant.now());
        interactionRepository.save(i);
    }

    private String knownCity(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.trim().toLowerCase();
        for (Hotel h : hotelRepository.findAll()) {
            if (h.getLocation() != null && h.getLocation().toLowerCase().equals(q)) {
                return h.getLocation();
            }
        }
        for (Flight f : flightRepository.findAll()) {
            if (f.getTo() != null && f.getTo().toLowerCase().equals(q)) {
                return f.getTo();
            }
            if (f.getFrom() != null && f.getFrom().toLowerCase().equals(q)) {
                return f.getFrom();
            }
        }
        return null;
    }

    public Map<String, Object> feedback(String userId, String itemType, String itemId, String value) {
        String kind = itemType == null ? "" : itemType.toUpperCase();
        String v = value == null ? "" : value.toUpperCase();
        if (!List.of("HOTEL", "FLIGHT", "DESTINATION").contains(kind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown item");
        }
        if (!List.of("HELPFUL", "IRRELEVANT", "NONE").contains(v)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Feedback must be HELPFUL or IRRELEVANT");
        }
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        }
        boolean exists = switch (kind) {
            case "HOTEL" -> hotelRepository.existsById(itemId);
            case "FLIGHT" -> flightRepository.existsById(itemId);
            default -> knownCity(itemId) != null;
        };
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Item not found");
        }
        if ("NONE".equals(v)) {
            feedbackRepository.deleteByUserIdAndItemTypeAndItemId(userId, kind, itemId);
            return Map.of("saved", true, "value", "NONE");
        }
        RecommendationFeedback f = feedbackRepository.findByUserIdAndItemTypeAndItemId(userId, kind, itemId).orElseGet(RecommendationFeedback::new);
        f.setUserId(userId);
        f.setItemType(kind);
        f.setItemId(itemId);
        f.setValue(v);
        f.setCreatedAt(Instant.now());
        feedbackRepository.save(f);
        return Map.of("saved", true, "value", v);
    }

    // ------------------------------------------------------------------ the recommender

    /** What we know about the taste of one traveller. */
    private static class Profile {
        final Map<String, Double> tags = new HashMap<>();             // tag -> interest
        final Map<String, String> evidence = new HashMap<>();         // tag -> "booked Taj Seaside Resort"
        final Map<String, Double> evidenceWeight = new HashMap<>();
        final Map<String, String> evidenceCity = new HashMap<>();
        final Map<String, Double> vector = new HashMap<>();           // "H:<hotel id>" / "C:<city>" -> weight (for collaborative filtering)
        final Set<String> booked = new HashSet<>();                   // "HOTEL:<id>" / "FLIGHT:<id>"
        final Set<String> hidden = new HashSet<>();                   // marked not relevant
        final Map<String, String> feedback = new HashMap<>();
        String lastOrigin;
        int bookings, views, searches, reviews, helpful, irrelevant;
        double signal;                                                // total positive evidence
        boolean usedPreferences;
    }

    private void addEvidence(Profile p, Collection<String> tags, double weight, String label, String city) {
        for (String t : tags) {
            p.tags.merge(t, weight, Double::sum);
            if (weight > 0 && weight > p.evidenceWeight.getOrDefault(t, 0.0)) {
                p.evidenceWeight.put(t, weight);
                p.evidence.put(t, label);
                p.evidenceCity.put(t, city);
            }
        }
    }

    public Map<String, Object> recommend(String userId, int limit, String typeFilter) {
        Instant now = Instant.now();
        int max = Math.max(1, Math.min(limit, 24));
        List<Hotel> hotels = hotelRepository.findAll();
        pricingService.enrichHotels(hotels);
        reviewService.enrichHotels(hotels);
        List<Flight> allFlights = flightRepository.findAll();
        Map<String, Hotel> hotelById = hotels.stream().collect(Collectors.toMap(Hotel::getId, h -> h));
        Map<String, Flight> flightById = allFlights.stream().collect(Collectors.toMap(Flight::getId, f -> f));

        // tags of a destination = the tags of the hotels there (so a flight to Bali is "beach, luxury, wellness ...")
        Map<String, Set<String>> cityTags = new HashMap<>();
        for (Hotel h : hotels) {
            cityTags.computeIfAbsent(h.getLocation(), k -> new LinkedHashSet<>()).addAll(h.getTags());
        }

        // upcoming flights: the next departure of every route
        Map<String, Flight> nextOnRoute = new LinkedHashMap<>();
        allFlights.stream()
                .filter(f -> {
                    Instant dep = TimeUtil.parse(f.getDepartureTime());
                    return dep != null && dep.isAfter(now.plus(Duration.ofHours(1))) && dep.isBefore(now.plus(Duration.ofDays(60))) && f.getAvailableSeats() > 0;
                })
                .sorted(Comparator.comparing(f -> TimeUtil.parse(f.getDepartureTime())))
                .forEach(f -> nextOnRoute.putIfAbsent(f.getFrom() + ">" + f.getTo(), f));

        // ---- all travellers (collaborative filtering) and the profile of this user
        List<Interaction> all = interactionRepository.findAll();
        Map<String, Map<String, Double>> vectors = new HashMap<>();
        Map<String, Double> hotelPopularity = new HashMap<>();
        Map<String, Double> cityPopularity = new HashMap<>();
        for (Interaction i : all) {
            double w = interactionWeight(i.getType()) * decay(i.getCreatedAt(), now);
            String city = cityOf(i, hotelById, flightById);
            if (city == null) {
                continue;
            }
            Map<String, Double> vec = vectors.computeIfAbsent(i.getUserId(), k -> new HashMap<>());
            if ("HOTEL".equals(i.getItemType())) {
                vec.merge("H:" + i.getItemId(), w, Double::sum);
                hotelPopularity.merge(i.getItemId(), w, Double::sum);
            }
            vec.merge("C:" + city, w, Double::sum);
            cityPopularity.merge(city, w, Double::sum);
        }

        Profile p = new Profile();
        boolean loggedIn = userId != null && !userId.isBlank() && userRepository.existsById(userId);
        if (loggedIn) {
            buildProfile(p, userId, now, hotelById, flightById, cityTags, vectors);
        }
        boolean personalised = p.signal >= 1.0;

        // ---- candidates
        List<Item> candidates = new ArrayList<>();
        boolean wantHotels = typeFilter == null || typeFilter.isBlank() || "HOTEL".equalsIgnoreCase(typeFilter);
        boolean wantFlights = typeFilter == null || typeFilter.isBlank() || "FLIGHT".equalsIgnoreCase(typeFilter);
        boolean wantDestinations = typeFilter == null || typeFilter.isBlank() || "DESTINATION".equalsIgnoreCase(typeFilter);
        boolean mixed = typeFilter == null || typeFilter.isBlank();
        if (wantHotels) {
            for (Hotel h : hotels) {
                String key = "HOTEL:" + h.getId();
                if (p.booked.contains(key) || p.hidden.contains(key)) {
                    continue;
                }
                Item it = new Item();
                it.setKey(key);
                it.setType("HOTEL");
                it.setItemId(h.getId());
                it.setTitle(h.gethotelName());
                it.setCity(h.getLocation());
                it.setSubtitle(h.getLocation());
                it.setImage(h.getImages().isEmpty() ? null : h.getImages().get(0));
                it.setPrice(h.getCurrentPricePerNight() > 0 ? h.getCurrentPricePerNight() : h.getPricePerNight());
                it.setTags(new ArrayList<>(h.getTags()));
                it.setRating(h.getAvgRating());
                it.setReviewCount(h.getReviewCount());
                it.setPriceChangePercent(h.getPriceChangePercent());
                it.setHref("/book-hotel/" + h.getId());
                candidates.add(it);
            }
        }
        if (wantFlights) {
            List<Flight> upcoming = new ArrayList<>(nextOnRoute.values());
            pricingService.enrichFlights(upcoming);
            reviewService.enrichFlights(upcoming);
            for (Flight f : upcoming) {
                String key = "FLIGHT:" + f.getId();
                if (p.booked.contains(key) || p.hidden.contains(key)) {
                    continue;
                }
                Item it = new Item();
                it.setKey(key);
                it.setType("FLIGHT");
                it.setItemId(f.getId());
                it.setTitle(f.getFlightName());
                it.setCity(f.getTo());
                it.setSubtitle(f.getFrom() + " → " + f.getTo());
                it.setPrice(f.getCurrentPrice() > 0 ? f.getCurrentPrice() : f.getPrice());
                it.setDepartureTime(f.getDepartureTime());
                it.setTags(new ArrayList<>(cityTags.getOrDefault(f.getTo(), Set.of())));
                it.setRating(f.getAvgRating());
                it.setReviewCount(f.getReviewCount());
                it.setPriceChangePercent(f.getPriceChangePercent());
                it.setHref("/book-flight/" + f.getId());
                candidates.add(it);
            }
        }

        if (wantDestinations) {
            // destinations: cities the traveller has not been to yet ("You liked beaches! Try Bali.")
            Set<String> visited = new HashSet<>();
            if (p.lastOrigin != null) {
                visited.add(p.lastOrigin.toLowerCase());
            }
            for (String b : p.booked) {
                String[] kv = b.split(":", 2);
                if ("HOTEL".equals(kv[0]) && hotelById.containsKey(kv[1])) {
                    visited.add(hotelById.get(kv[1]).getLocation().toLowerCase());
                } else if ("FLIGHT".equals(kv[0]) && flightById.containsKey(kv[1])) {
                    visited.add(flightById.get(kv[1]).getTo().toLowerCase());
                }
            }
            Map<String, List<Hotel>> byCity = new LinkedHashMap<>();
            for (Hotel h : hotels) {
                byCity.computeIfAbsent(h.getLocation(), k -> new ArrayList<>()).add(h);
            }
            for (Map.Entry<String, List<Hotel>> e : byCity.entrySet()) {
                String city = e.getKey();
                String key = "DESTINATION:" + city;
                if (visited.contains(city.toLowerCase()) || p.hidden.contains(key)) {
                    continue;
                }
                List<Hotel> inCity = e.getValue();
                Hotel top = inCity.stream().max(Comparator.comparingDouble((Hotel h) -> h.getReviewCount() > 0 ? h.getAvgRating() : 3.0)
                        .thenComparingDouble(h -> -h.getPricePerNight())).orElse(inCity.get(0));
                Item it = new Item();
                it.setKey(key);
                it.setType("DESTINATION");
                it.setItemId(city);
                it.setTitle(city);
                it.setCity(city);
                long routes = nextOnRoute.values().stream().filter(f -> city.equalsIgnoreCase(f.getTo())).count();
                it.setSubtitle(inCity.size() + (inCity.size() == 1 ? " stay" : " stays") + (routes > 0 ? " · " + routes + (routes == 1 ? " route" : " routes") + " by air" : ""));
                it.setImage(top.getImages().isEmpty() ? null : top.getImages().get(0));
                it.setPrice(inCity.stream().mapToDouble(h -> h.getCurrentPricePerNight() > 0 ? h.getCurrentPricePerNight() : h.getPricePerNight()).min().orElse(0));
                it.setTags(new ArrayList<>(cityTags.getOrDefault(city, Set.of())));
                int reviews = inCity.stream().mapToInt(Hotel::getReviewCount).sum();
                it.setReviewCount(reviews);
                it.setRating(reviews == 0 ? 0 : inCity.stream().mapToDouble(h -> h.getAvgRating() * h.getReviewCount()).sum() / reviews);
                it.setHref("/book-hotel/" + top.getId());
                candidates.add(it);
            }
        }

        // ---- collaborative filtering: the travellers who are most like this one
        List<Map.Entry<String, Double>> neighbours = new ArrayList<>();
        Map<String, Double> mine = vectors.getOrDefault(userId, Map.of());
        if (loggedIn && !mine.isEmpty()) {
            for (Map.Entry<String, Map<String, Double>> e : vectors.entrySet()) {
                if (e.getKey().equals(userId)) {
                    continue;
                }
                double sim = cosine(mine, e.getValue());
                if (sim > 0.05) {
                    neighbours.add(Map.entry(e.getKey(), sim));
                }
            }
            neighbours.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            if (neighbours.size() > NEIGHBOURS) {
                neighbours = new ArrayList<>(neighbours.subList(0, NEIGHBOURS));
            }
        }
        final List<Map.Entry<String, Double>> peers = neighbours;

        double maxHotelPop = hotelPopularity.values().stream().mapToDouble(d -> d).max().orElse(1);
        double maxCityPop = cityPopularity.values().stream().mapToDouble(d -> d).max().orElse(1);
        Map<String, Double> rawCf = new HashMap<>();
        Map<String, Integer> peerCount = new HashMap<>();
        Map<String, Double> topSim = new HashMap<>();
        for (Item it : candidates) {
            double cf = 0;
            int count = 0;
            double best = 0;
            double simSum = peers.stream().mapToDouble(Map.Entry::getValue).sum();
            if (simSum > 0) {
                double sumH = 0, sumC = 0;
                for (Map.Entry<String, Double> n : peers) {
                    Map<String, Double> vec = vectors.get(n.getKey());
                    double top = vec.values().stream().mapToDouble(d -> d).max().orElse(1);
                    double c = vec.getOrDefault("C:" + it.getCity(), 0.0) / top;
                    double h = "HOTEL".equals(it.getType()) ? vec.getOrDefault("H:" + it.getItemId(), 0.0) / top : 0;
                    sumC += n.getValue() * c;
                    sumH += n.getValue() * h;
                    if (c > 0 || h > 0) {
                        count++;
                        best = Math.max(best, n.getValue());
                    }
                }
                cf = "HOTEL".equals(it.getType()) ? 0.6 * (sumH / simSum) + 0.4 * (sumC / simSum) : sumC / simSum;
            }
            rawCf.put(it.getKey(), cf);
            peerCount.put(it.getKey(), count);
            topSim.put(it.getKey(), best);
        }
        double maxCf = rawCf.values().stream().mapToDouble(d -> d).max().orElse(0);

        // ---- score every candidate
        Map<String, Double> scores = new HashMap<>();
        Map<String, double[]> parts = new HashMap<>();       // cb, cf, pop, deal
        for (Item it : candidates) {
            Map<String, Double> itemVec = new HashMap<>();
            for (String t : it.getTags()) {
                itemVec.put(t, 1.0);
            }
            double cb = personalised ? cosine(p.tags, itemVec) : 0;
            double cf = maxCf > 0 ? rawCf.get(it.getKey()) / maxCf : 0;
            double pop;
            if ("HOTEL".equals(it.getType())) {
                double seen = hotelPopularity.getOrDefault(it.getItemId(), 0.0) / maxHotelPop;
                double rating = it.getReviewCount() > 0 ? it.getRating() / 5.0 : 0.6;
                pop = 0.6 * seen + 0.4 * rating;
            } else {
                pop = cityPopularity.getOrDefault(it.getCity(), 0.0) / maxCityPop;
            }
            double deal = it.getPriceChangePercent() < -1 ? Math.min(1, -it.getPriceChangePercent() / 10.0) : 0;
            double score;
            if (personalised) {
                score = 0.50 * cb + 0.30 * cf + 0.12 * pop + 0.08 * deal;
                if ("FLIGHT".equals(it.getType()) && p.lastOrigin != null && !it.getSubtitle().startsWith(p.lastOrigin + " ")) {
                    score *= 0.85;      // prefer flights that leave from the city the traveller usually flies from
                }
            } else {
                score = 0.80 * pop + 0.20 * deal;
            }
            scores.put(it.getKey(), score);
            parts.put(it.getKey(), new double[]{cb, cf, pop, deal});
        }

        // ---- pick: best first, but not only one city and not only one kind of item
        candidates.sort((a, b) -> Double.compare(scores.get(b.getKey()), scores.get(a.getKey())));
        List<Item> picked = new ArrayList<>();
        Map<String, Integer> perCity = new HashMap<>();
        List<Item> pool = new ArrayList<>(candidates);
        int wanted = Math.min(max, pool.size());
        int destCap = mixed ? Math.max(1, max / 3) : max;
        int flightCap = mixed ? Math.max(2, max / 3) : max;
        if (mixed) {
            // the best destination always gets a place: it is the "try something new" suggestion
            pool.stream().filter(c -> "DESTINATION".equals(c.getType())).findFirst().ifPresent(d -> {
                pool.remove(d);
                picked.add(d);
                perCity.merge(d.getCity(), 1, Integer::sum);
            });
        }
        while (picked.size() < wanted && !pool.isEmpty()) {
            Item best = null;
            double bestScore = -1;
            for (Item c : pool) {
                double sc = scores.get(c.getKey()) * Math.pow(0.88, perCity.getOrDefault(c.getCity(), 0));
                if (sc > bestScore) {
                    bestScore = sc;
                    best = c;
                }
            }
            final Item chosen = best;
            pool.remove(chosen);
            perCity.merge(chosen.getCity(), 1, Integer::sum);
            long ofType = picked.stream().filter(x -> x.getType().equals(chosen.getType())).count();
            int cap = "DESTINATION".equals(chosen.getType()) ? destCap : "FLIGHT".equals(chosen.getType()) ? flightCap : max;
            if (ofType >= cap) {
                continue;
            }
            scores.put(chosen.getKey(), bestScore);
            picked.add(chosen);
        }

        for (Item it : picked) {
            double[] s = parts.get(it.getKey());
            explain(it, s, scores.get(it.getKey()), p, personalised, peerCount.getOrDefault(it.getKey(), 0), topSim.getOrDefault(it.getKey(), 0.0));
            it.setFeedback(p.feedback.get(it.getKey()));
        }
        picked.sort((a, b) -> Double.compare(b.getMatchPercent(), a.getMatchPercent()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("strategy", personalised ? "PERSONALIZED" : "TRENDING");
        out.put("title", personalised ? "Recommended for you" : "Trending right now");
        out.put("items", picked);
        out.put("profile", profileSummary(p, personalised, peers.size()));
        return out;
    }

    private String cityOf(Interaction i, Map<String, Hotel> hotels, Map<String, Flight> flights) {
        switch (i.getItemType()) {
            case "HOTEL":
                Hotel h = hotels.get(i.getItemId());
                return h == null ? null : h.getLocation();
            case "FLIGHT":
                Flight f = flights.get(i.getItemId());
                return f == null ? null : f.getTo();
            case "DESTINATION":
                return i.getItemId();
            default:
                return null;
        }
    }

    private void buildProfile(Profile p, String userId, Instant now, Map<String, Hotel> hotelById, Map<String, Flight> flightById,
                              Map<String, Set<String>> cityTags, Map<String, Map<String, Double>> vectors) {
        for (Interaction i : interactionRepository.findByUserId(userId)) {
            double w = interactionWeight(i.getType()) * decay(i.getCreatedAt(), now);
            if ("BOOK".equals(i.getType())) {
                p.bookings++;
                p.booked.add(i.getItemType() + ":" + i.getItemId());
            } else if ("SEARCH".equals(i.getType())) {
                p.searches++;
            } else {
                p.views++;
            }
            if ("HOTEL".equals(i.getItemType())) {
                Hotel h = hotelById.get(i.getItemId());
                if (h != null) {
                    addEvidence(p, h.getTags(), w, label(i.getType(), h.gethotelName()), h.getLocation());
                    p.signal += w;
                }
            } else if ("FLIGHT".equals(i.getItemType())) {
                Flight f = flightById.get(i.getItemId());
                if (f != null) {
                    addEvidence(p, cityTags.getOrDefault(f.getTo(), Set.of()), w, label(i.getType(), "flight to " + f.getTo()), f.getTo());
                    p.signal += w;
                    if ("BOOK".equals(i.getType()) || p.lastOrigin == null) {
                        p.lastOrigin = f.getFrom();
                    }
                }
            } else if ("DESTINATION".equals(i.getItemType())) {
                addEvidence(p, cityTags.getOrDefault(i.getItemId(), Set.of()), w, "searched for " + i.getItemId(), i.getItemId());
                p.signal += w;
            }
        }
        // reviews
        for (Review r : reviewRepository.findByUserId(userId)) {
            if (!"PUBLISHED".equals(r.getStatus())) {
                continue;
            }
            double base = r.getRating() >= 4 ? W_REVIEW_GOOD : (r.getRating() <= 2 ? W_REVIEW_BAD : 0.5);
            double w = base * decay(r.getCreatedAt(), now);
            Collection<String> tags = List.of();
            String city = null;
            if ("HOTEL".equals(r.getTargetType()) && hotelById.containsKey(r.getTargetId())) {
                Hotel h = hotelById.get(r.getTargetId());
                tags = h.getTags();
                city = h.getLocation();
            } else if ("FLIGHT".equals(r.getTargetType()) && flightById.containsKey(r.getTargetId())) {
                city = flightById.get(r.getTargetId()).getTo();
                tags = cityTags.getOrDefault(city, Set.of());
            }
            if (!tags.isEmpty()) {
                addEvidence(p, tags, w, (r.getRating() >= 4 ? "rated " : "reviewed ") + r.getTargetName() + " " + r.getRating() + "★", city);
                p.reviews++;
                if (w > 0) {
                    p.signal += w;
                }
            }
        }
        // saved seat / room preferences
        Users u = userRepository.findById(userId).orElse(null);
        if (u != null && u.getPreferences() != null) {
            Users.Preferences pr = u.getPreferences();
            boolean premium = "PREMIUM".equalsIgnoreCase(pr.getSeatTier()) || "EXTRA_LEGROOM".equalsIgnoreCase(pr.getSeatTier())
                    || "Suite".equalsIgnoreCase(pr.getRoomType()) || "Deluxe".equalsIgnoreCase(pr.getRoomType());
            if (premium) {
                addEvidence(p, List.of("luxury"), W_PREFERENCE, "saved a premium seat / room preference", null);
                p.signal += W_PREFERENCE;
                p.usedPreferences = true;
            }
        }
        // feedback loop
        for (RecommendationFeedback f : feedbackRepository.findByUserId(userId)) {
            String key = f.getItemType() + ":" + f.getItemId();
            p.feedback.put(key, f.getValue());
            Collection<String> tags = List.of();
            String name = "";
            if ("HOTEL".equals(f.getItemType()) && hotelById.containsKey(f.getItemId())) {
                Hotel h = hotelById.get(f.getItemId());
                tags = h.getTags();
                name = h.gethotelName();
            } else if ("FLIGHT".equals(f.getItemType()) && flightById.containsKey(f.getItemId())) {
                String city = flightById.get(f.getItemId()).getTo();
                tags = cityTags.getOrDefault(city, Set.of());
                name = "flight to " + city;
            } else if ("DESTINATION".equals(f.getItemType())) {
                tags = cityTags.getOrDefault(f.getItemId(), Set.of());
                name = f.getItemId();
            }
            double decay = decay(f.getCreatedAt(), now);
            if ("HELPFUL".equals(f.getValue())) {
                p.helpful++;
                addEvidence(p, tags, W_HELPFUL * decay, "found " + name + " helpful", null);
                p.signal += W_HELPFUL * decay;
            } else {
                p.irrelevant++;
                addEvidence(p, tags, W_IRRELEVANT * decay, "", null);
                if (f.getCreatedAt() != null && f.getCreatedAt().isAfter(now.minus(IRRELEVANT_FOR))) {
                    p.hidden.add(key);
                }
            }
        }
        p.tags.replaceAll((k, v) -> Math.max(0, v));
        p.tags.values().removeIf(v -> v <= 0);
    }

    private static String label(String type, String what) {
        return switch (type) {
            case "BOOK" -> "booked " + what;
            case "SEARCH" -> "searched for " + what;
            default -> "looked at " + what;
        };
    }

    private static String phrase(String tag) {
        return PHRASE.getOrDefault(tag, tag);
    }

    private void explain(Item it, double[] s, double score, Profile p, boolean personalised, int peers, double topSim) {
        double cb = s[0], cf = s[1], pop = s[2], deal = s[3];
        it.setMatchPercent((int) Math.max(1, Math.min(99, Math.round(score * 100))));

        // the interest of the user that fits this item best
        String bestTag = null;
        double bestWeight = 0;
        for (String t : it.getTags()) {
            double w = p.tags.getOrDefault(t, 0.0);
            if (w > bestWeight) {
                bestWeight = w;
                bestTag = t;
            }
        }
        List<String> why = new ArrayList<>();
        List<String> algorithms = new ArrayList<>();
        String headline;
        String action = "FLIGHT".equals(it.getType()) ? "Fly to " + it.getCity() + "." : "Try " + it.getCity() + ".";

        if (personalised && bestTag != null && cb >= 0.15) {
            List<String> shared = it.getTags().stream().filter(t -> p.tags.containsKey(t))
                    .sorted((a, b) -> Double.compare(p.tags.get(b), p.tags.get(a))).limit(3).collect(Collectors.toList());
            String evidence = p.evidence.get(bestTag);
            String evidenceCity = p.evidenceCity.get(bestTag);
            boolean sameCity = evidenceCity != null && evidenceCity.equalsIgnoreCase(it.getCity());
            boolean sameItem = evidence != null && evidence.endsWith(it.getTitle());
            headline = "You liked " + phrase(bestTag) + "! " + (sameItem
                    ? "Pick up where you left off."
                    : sameCity ? "Here's another great option in " + it.getCity() + "." : action);
            if (evidence != null) {
                why.add("You " + evidence + ", which is about " + phrase(bestTag) + ".");
            }
            why.add("This " + ("HOTEL".equals(it.getType()) ? "hotel" : "destination") + " matches your interest in "
                    + shared.stream().map(RecommendationService::phrase).collect(Collectors.joining(", ")) + " (" + Math.round(cb * 100) + "% match with your taste profile).");
            algorithms.add("Content-based");
        } else if (personalised && cf > 0.2 && peers > 0) {
            headline = "Travellers with a taste like yours chose this";
        } else if (deal > 0) {
            headline = "Price drop: " + Math.round(-it.getPriceChangePercent()) + "% cheaper than recently";
        } else if (it.getReviewCount() > 0 && it.getRating() >= 4.0) {
            headline = "Top rated" + ("HOTEL".equals(it.getType()) ? " in " + it.getCity() : "DESTINATION".equals(it.getType()) ? " destination" : " route");
        } else {
            headline = "Popular with travellers right now";
        }
        if (personalised && p.usedPreferences && it.getTags().contains("luxury")) {
            why.add("It fits the premium seat / room preference you saved.");
        }
        if (personalised && cf > 0.2 && peers > 0) {
            why.add(peers + " traveller" + (peers == 1 ? "" : "s") + " with similar trips to yours booked or looked at this (up to " + Math.min(99, Math.round(topSim * 100)) + "% similar).");
            algorithms.add("Collaborative filtering");
        }
        if (it.getReviewCount() > 0) {
            why.add("Rated " + String.format(Locale.ROOT, "%.1f", it.getRating()) + "★ by " + it.getReviewCount() + " traveller" + (it.getReviewCount() == 1 ? "" : "s") + ".");
        }
        if (pop >= 0.5) {
            why.add("It is popular with other travellers at the moment.");
            algorithms.add("Popularity");
        } else if (!personalised) {
            algorithms.add("Popularity");
        }
        if (deal > 0) {
            why.add("The price is " + Math.round(-it.getPriceChangePercent()) + "% lower than it was recently.");
            algorithms.add("Price deal");
        }
        if (p.helpful > 0 && bestTag != null) {
            why.add("You marked similar suggestions as helpful before.");
        }
        if (!personalised) {
            why.add("Log in and book, search or rate a few things so we can tailor this to you.");
        }
        if (algorithms.isEmpty()) {
            algorithms.add("Popularity");
        }
        it.setHeadline(headline);
        it.setReasons(why);
        it.setAlgorithms(algorithms);
    }

    private Map<String, Object> profileSummary(Profile p, boolean personalised, int similarTravellers) {
        double top = p.tags.values().stream().mapToDouble(d -> d).max().orElse(1);
        List<Map<String, Object>> interests = p.tags.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(6)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tag", e.getKey());
                    m.put("label", phrase(e.getKey()));
                    m.put("percent", (int) Math.round(e.getValue() / top * 100));
                    return m;
                }).collect(Collectors.toList());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("personalised", personalised);
        m.put("interests", interests);
        m.put("bookings", p.bookings);
        m.put("views", p.views);
        m.put("searches", p.searches);
        m.put("reviews", p.reviews);
        m.put("helpful", p.helpful);
        m.put("notRelevant", p.irrelevant);
        m.put("similarTravellers", similarTravellers);
        return m;
    }
}
