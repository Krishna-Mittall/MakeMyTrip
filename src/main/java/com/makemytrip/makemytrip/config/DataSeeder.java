package com.makemytrip.makemytrip.config;

import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.repositories.UserRepository;
import com.makemytrip.makemytrip.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Fills an empty database with some flights, hotels and the admin / moderator accounts so that the
 * application can be tried out straight away. It only runs when the collections are empty.
 * Set app.seed.enabled=false to switch it off.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        createUser("Admin", "User", "admin@makemytour.com", "Admin@123", "ADMIN");
        createUser("Moderator", "User", "moderator@makemytour.com", "Mod@12345", "MODERATOR");
        if (flightRepository.count() == 0) {
            seedFlights();
        }
        if (hotelRepository.count() == 0) {
            seedHotels();
        }
    }

    private void createUser(String firstName, String lastName, String email, String password, String role) {
        if (userRepository.findByEmail(email) != null) {
            return;
        }
        Users user = new Users();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhoneNumber("9999999999");
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEmail(email);
        userRepository.save(user);
    }

    // ------------------------------------------------------------------ flights

    private static final Object[][] ROUTES = {
            // from, to, duration (min), base price, seats
            {"Delhi", "Mumbai", 125, 5200, 150},
            {"Mumbai", "Delhi", 130, 5400, 160},
            {"Delhi", "Bengaluru", 175, 6100, 180},
            {"Bengaluru", "Delhi", 180, 5900, 170},
            {"Delhi", "Goa", 165, 6800, 150},
            {"Goa", "Delhi", 170, 6600, 150},
            {"Mumbai", "Goa", 70, 3100, 120},
            {"Mumbai", "Bengaluru", 100, 4400, 180},
            {"Delhi", "Kolkata", 135, 6400, 170},
            {"Delhi", "Jaipur", 60, 3300, 90},
            {"Delhi", "Shimla", 80, 4800, 72},
            {"Delhi", "Udaipur", 90, 4500, 100},
            {"Delhi", "Bali", 480, 21800, 200},
            {"Mumbai", "Bali", 540, 19500, 220},
            {"Chennai", "Hyderabad", 85, 3900, 150},
            {"Bengaluru", "Goa", 70, 3500, 120},
    };
    private static final String[] AIRLINES = {"IndiGo 6E", "Air India AI", "Vistara UK", "SpiceJet SG", "Akasa QP"};

    private void seedFlights() {
        Random random = new Random(42);
        LocalDateTime today = TimeUtil.now().toLocalDateTime().withMinute(0).withSecond(0).withNano(0);
        List<Flight> flights = new ArrayList<>();
        int[] nearOffsets = {1, 2, 3, 4, 5, 6, 7, 9};
        int[] farOffsets = {14, 21, 30, 45, 60, 75, 90};
        int n = 0;
        for (Object[] route : ROUTES) {
            for (int k = 0; k < 3; k++) {
                int offset;
                if (k == 0) {
                    offset = nearOffsets[random.nextInt(nearOffsets.length)];
                } else if (k == 1) {
                    offset = nearOffsets[random.nextInt(nearOffsets.length)] + 8;
                } else {
                    offset = farOffsets[random.nextInt(farOffsets.length)];
                }
                int hour = 5 + random.nextInt(18);
                int minute = random.nextInt(4) * 15;
                LocalDateTime dep = today.plusDays(offset).withHour(hour).withMinute(minute);
                LocalDateTime arr = dep.plusMinutes((int) route[2]);
                String airline = AIRLINES[(n + k) % AIRLINES.length];
                Flight f = new Flight();
                f.setFlightName(airline + "-" + (100 + random.nextInt(900)));
                f.setFrom((String) route[0]);
                f.setTo((String) route[1]);
                f.setDepartureTime(dep.format(FORMAT));
                f.setArrivalTime(arr.format(FORMAT));
                int price = (int) route[3];
                f.setPrice(Math.round(price * (0.92 + random.nextDouble() * 0.2) / 10.0) * 10);
                // some flights are almost full, others nearly empty, so that the demand based prices differ
                f.setAvailableSeats((int) Math.round((int) route[4] * (0.30 + 0.65 * random.nextDouble())));
                flights.add(f);
            }
            n++;
        }
        flightRepository.saveAll(flights);
        seedDailyFlights(random);
    }

    /**
     * A handful of "daily" flights that leave in the next hours, so that the live flight status always has something
     * to show (boarding, delayed, in the air ...). When one has landed it is moved to the next day (see FlightStatusService).
     */
    private void seedDailyFlights(Random random) {
        int[] minutesFromNow = {55, 110, 190, 300, 460, 640, 900, 1250};
        LocalDateTime now = TimeUtil.now().toLocalDateTime().withSecond(0).withNano(0);
        List<Flight> flights = new ArrayList<>();
        for (int i = 0; i < minutesFromNow.length; i++) {
            Object[] route = ROUTES[(i * 2) % ROUTES.length];
            LocalDateTime dep = now.plusMinutes(minutesFromNow[i]);
            dep = dep.withMinute((dep.getMinute() / 5) * 5);
            Flight f = new Flight();
            f.setFlightName(AIRLINES[i % AIRLINES.length] + "-" + (700 + i * 11 + random.nextInt(9)));
            f.setFrom((String) route[0]);
            f.setTo((String) route[1]);
            f.setDepartureTime(dep.format(FORMAT));
            f.setArrivalTime(dep.plusMinutes((int) route[2]).format(FORMAT));
            f.setPrice(Math.round((int) route[3] * 1.05 / 10.0) * 10);
            f.setAvailableSeats((int) Math.round((int) route[4] * (0.45 + 0.4 * random.nextDouble())));
            f.setAutoRoll(true);
            flights.add(f);
        }
        flightRepository.saveAll(flights);
    }

    // ------------------------------------------------------------------ hotels

    private static final String[] PHOTOS = {
            "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1587474260584-136574528ed5?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1520454974749-611b7248ffdb?auto=format&fit=crop&w=800",
    };

    private void seedHotels() {
        Object[][] data = {
                // name, location, price, rooms, amenities, tags
                {"Taj Seaside Resort", "Goa", 9500, 40, "Beach Access, Wi-Fi, Pool, Restaurant, Water Sports", "beach,luxury,nightlife"},
                {"Candolim Beach Retreat", "Goa", 4200, 60, "Beach Access, Wi-Fi, Restaurant, Bar", "beach,budget,nightlife"},
                {"The Imperial Palace", "Delhi", 14000, 50, "Wi-Fi, Spa, Pool, Fine Dining, Airport Transfer", "city,luxury,spa"},
                {"Comfort Inn Connaught", "Delhi", 3800, 80, "Wi-Fi, Restaurant, Gym, Free Breakfast", "city,budget"},
                {"Marine Drive Grand", "Mumbai", 11000, 45, "Sea View, Wi-Fi, Spa, Restaurant, Gym", "city,luxury,sea view"},
                {"Juhu Beach Hotel", "Mumbai", 6500, 60, "Beach Access, Wi-Fi, Pool, Restaurant", "beach,city"},
                {"Mountain Lodge", "Shimla", 5200, 30, "Fireplace, Hot Tub, Restaurant, Mountain View", "mountain,nature,snow"},
                {"Himalayan Heights Resort", "Shimla", 8200, 25, "Ski-in/Ski-out, Spa, Fireplace, Restaurant", "mountain,luxury,spa"},
                {"Pink City Haveli", "Jaipur", 5800, 35, "Heritage Rooms, Wi-Fi, Rooftop Restaurant, Cultural Shows", "heritage,culture"},
                {"Lake Palace View", "Udaipur", 12500, 28, "Lake View, Wi-Fi, Spa, Fine Dining, Boat Rides", "heritage,luxury,lake"},
                {"Bali Seaside Resort", "Bali", 16000, 50, "Beach Access, Pool, Spa, Wi-Fi, Water Sports", "beach,luxury,wellness"},
                {"Ubud Jungle Villas", "Bali", 9800, 30, "Private Pool, Yoga, Spa, Wi-Fi, Nature Walks", "nature,wellness,culture"},
                {"Garden City Suites", "Bengaluru", 6200, 70, "Wi-Fi, Gym, Business Centre, Restaurant", "city,business"},
                {"Marina Bay Residency", "Chennai", 5000, 55, "Wi-Fi, Pool, Restaurant, Beach Nearby", "city,beach"},
                {"Charminar Heritage Inn", "Hyderabad", 4600, 45, "Wi-Fi, Restaurant, Heritage Rooms, Pool", "heritage,city"},
                {"Howrah Bridge Hotel", "Kolkata", 4300, 50, "Wi-Fi, Restaurant, River View, Gym", "heritage,city"},
        };
        List<Hotel> hotels = new ArrayList<>();
        int i = 0;
        for (Object[] row : data) {
            Hotel h = new Hotel();
            h.sethotelName((String) row[0]);
            h.setLocation((String) row[1]);
            h.setPricePerNight((int) row[2]);
            h.setAvailableRooms((int) row[3]);
            h.setamenities((String) row[4]);
            h.setTags(new ArrayList<>(Arrays.asList(((String) row[5]).split(","))));
            List<String> images = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                images.add(PHOTOS[(i + k) % PHOTOS.length]);
            }
            h.setImages(images);
            hotels.add(h);
            i++;
        }
        hotelRepository.saveAll(hotels);
    }
}
