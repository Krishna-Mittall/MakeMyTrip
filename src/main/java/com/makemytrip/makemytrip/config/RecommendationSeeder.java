package com.makemytrip.makemytrip.config;

import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.Interaction;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.repositories.InteractionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collaborative filtering needs other travellers to learn from. On an empty database this creates the booking and
 * browsing history of 30 sample travellers (5 kinds of taste, 6 travellers each). They are only rows in the
 * "interactions" collection - there are no accounts behind them.
 */
@Component
@Order(2)
public class RecommendationSeeder implements CommandLineRunner {
    private static final String[][] PERSONAS = {
            {"beach", "nightlife", "budget"},
            {"mountain", "nature", "snow"},
            {"heritage", "culture", "lake"},
            {"city", "business"},
            {"luxury", "spa", "wellness"},
    };

    @Autowired
    private InteractionRepository interactionRepository;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled || interactionRepository.count() > 0) {
            return;
        }
        List<Hotel> hotels = hotelRepository.findAll();
        List<Flight> flights = flightRepository.findAll();
        if (hotels.isEmpty()) {
            return;
        }
        Random random = new Random(7);
        List<Interaction> rows = new ArrayList<>();
        int user = 1;
        for (String[] persona : PERSONAS) {
            Set<String> likes = new HashSet<>(Arrays.asList(persona));
            List<Hotel> matching = hotels.stream().filter(h -> h.getTags().stream().anyMatch(likes::contains)).collect(Collectors.toList());
            for (int k = 0; k < 6; k++, user++) {
                String userId = String.format("demo-traveller-%02d", user);
                int count = 4 + random.nextInt(3);
                Set<String> used = new HashSet<>();
                for (int n = 0; n < count; n++) {
                    boolean onTaste = random.nextInt(100) < 80 && !matching.isEmpty();
                    Hotel h = onTaste ? matching.get(random.nextInt(matching.size())) : hotels.get(random.nextInt(hotels.size()));
                    if (!used.add(h.getId())) {
                        continue;
                    }
                    boolean booked = n < 2 || random.nextInt(100) < 25;
                    rows.add(row(userId, "HOTEL", h.getId(), booked ? "BOOK" : "VIEW", random));
                    if (booked && random.nextInt(100) < 55) {
                        List<Flight> toCity = flights.stream().filter(f -> f.getTo().equals(h.getLocation())).collect(Collectors.toList());
                        if (!toCity.isEmpty()) {
                            rows.add(row(userId, "FLIGHT", toCity.get(random.nextInt(toCity.size())).getId(), "BOOK", random));
                        }
                    }
                    if (random.nextInt(100) < 40) {
                        rows.add(row(userId, "DESTINATION", h.getLocation(), "SEARCH", random));
                    }
                }
            }
        }
        interactionRepository.saveAll(rows);
    }

    private Interaction row(String userId, String itemType, String itemId, String type, Random random) {
        Interaction i = new Interaction();
        i.setUserId(userId);
        i.setItemType(itemType);
        i.setItemId(itemId);
        i.setType(type);
        i.setDemo(true);
        i.setCreatedAt(Instant.now().minus(Duration.ofHours(6 + random.nextInt(24 * 40))));
        return i;
    }
}
