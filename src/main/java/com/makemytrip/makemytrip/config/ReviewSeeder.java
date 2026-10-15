package com.makemytrip.makemytrip.config;

import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.Review;
import com.makemytrip.makemytrip.models.ReviewPhoto;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.repositories.ReviewPhotoRepository;
import com.makemytrip.makemytrip.repositories.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;

/**
 * A review section that is empty cannot show sorting, filtering, replies or moderation. On an empty "reviews"
 * collection this adds some sample reviews from sample travellers (there are no accounts behind them), a few
 * replies and helpful votes, and two flagged reviews that wait in the moderator queue. Switch it off with
 * app.seed.enabled=false.
 */
@Component
@Order(3)
public class ReviewSeeder implements CommandLineRunner {
    private static final String[] NAMES = {"Aarav S.", "Diya M.", "Rohan K.", "Ananya P.", "Vikram R.", "Meera N.", "Kabir T.", "Ishita B.",
            "Arjun D.", "Sneha G.", "Rahul J.", "Priya L.", "Neil A.", "Tara V.", "Yash C.", "Zoya H."};

    private static final String[][] HOTEL_5 = {
            {"Perfect stay", "Spotless rooms, very friendly staff and a great breakfast. The location in %s is excellent and check-in took two minutes."},
            {"Worth every rupee", "We stayed for three nights and everything was smooth. The room was bigger than expected and the bed was really comfortable."},
            {"Would come back", "Loved the service. The staff arranged a late checkout without any fuss and the food was fresh and tasty."}};
    private static final String[][] HOTEL_4 = {
            {"Very good, small niggles", "Great value and clean rooms. The Wi-Fi was a bit slow in the evenings, otherwise a really nice stay in %s."},
            {"Good for families", "Our kids enjoyed it. Rooms were clean and quiet, breakfast had plenty of choice. Parking was a little tight."}};
    private static final String[][] HOTEL_3 = {
            {"Decent but average", "The room was fine and the staff polite, but it felt a little dated and the water pressure was low in the morning."}};
    private static final String[][] HOTEL_2 = {
            {"Not what I expected", "Photos looked better than the room. Housekeeping was slow and we had to ask twice for fresh towels."}};
    private static final String[][] FLIGHT_5 = {
            {"On time and smooth", "Boarding was quick, the crew was helpful and we landed ahead of schedule. Seats were comfortable for a short flight."},
            {"Great value", "Booked at a good price, no surprises. Legroom was fine and the app updates about the gate were accurate."}};
    private static final String[][] FLIGHT_4 = {
            {"Good flight", "Departed a few minutes late but made up the time. Cabin was clean and the snacks were decent."}};
    private static final String[][] FLIGHT_3 = {
            {"Average experience", "Check-in queue was long and the flight left 40 minutes late. The crew was polite though."}};
    private static final String[][] FLIGHT_2 = {
            {"Delayed and crowded", "Over an hour of delay with little information at the gate. The flight itself was fine."}};

    private static final String[] REPLIES = {"Thanks for sharing this, very helpful!", "Same experience for me last month.",
            "Did you book the deluxe room or the standard one?", "Good to know, I am planning to go in November.",
            "Agree about the breakfast, it was the best part.", "Thanks, this helped me decide."};

    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ReviewPhotoRepository photoRepository;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled || reviewRepository.count() > 0) {
            return;
        }
        List<Hotel> hotels = hotelRepository.findAll();
        List<Flight> flights = flightRepository.findAll();
        if (hotels.isEmpty()) {
            return;
        }
        Random random = new Random(11);
        List<String> photoIds = createPhotos();
        List<Review> rows = new ArrayList<>();

        // every hotel gets 3-5 reviews, so the star ratings show up on all search cards
        int photoCursor = 0;
        for (Hotel h : hotels) {
            int count = 3 + random.nextInt(3);
            Set<Integer> usedNames = new HashSet<>();
            for (int n = 0; n < count; n++) {
                int rating = pickRating(random);
                Review r = base("HOTEL", h.getId(), h.gethotelName(), nameIndex(random, usedNames), random);
                String[] t = pick(random, rating >= 5 ? HOTEL_5 : rating == 4 ? HOTEL_4 : rating == 3 ? HOTEL_3 : HOTEL_2);
                fill(r, rating, t[0], String.format(t[1], h.getLocation()), random);
                if (n == 0 && !photoIds.isEmpty() && photoCursor < photoIds.size() * 2) {
                    r.getPhotoIds().add(photoIds.get(photoCursor % photoIds.size()));
                    photoCursor++;
                }
                rows.add(r);
            }
        }
        // some popular flight routes get reviews too
        Map<String, Integer> perRoute = new HashMap<>();
        for (Flight f : flights) {
            String route = f.getFrom() + "-" + f.getTo();
            if (perRoute.merge(route, 1, Integer::sum) > 1 || rows.size() > 90) {
                continue;
            }
            int count = 2 + random.nextInt(2);
            Set<Integer> usedNames = new HashSet<>();
            for (int n = 0; n < count; n++) {
                int rating = pickRating(random);
                Review r = base("FLIGHT", f.getId(), f.getFlightName() + " (" + f.getFrom() + " - " + f.getTo() + ")", nameIndex(random, usedNames), random);
                String[] t = pick(random, rating >= 5 ? FLIGHT_5 : rating == 4 ? FLIGHT_4 : rating == 3 ? FLIGHT_3 : FLIGHT_2);
                fill(r, rating, t[0], t[1], random);
                rows.add(r);
            }
        }

        // two reviews that other travellers flagged, waiting for a moderator
        Hotel first = hotels.get(0);
        Review spam = base("HOTEL", first.getId(), first.gethotelName(), 5, random);
        fill(spam, 1, "BEST DEAL!!!", "Skip this place and visit www.cheap-holiday-deals.example for 90% off every hotel, message us on WhatsApp now!!!", random);
        flag(spam, "Spam", 2);
        rows.add(spam);
        Hotel second = hotels.get(Math.min(3, hotels.size() - 1));
        Review rude = base("HOTEL", second.getId(), second.gethotelName(), 9, random);
        fill(rude, 1, "Terrible", "Horrible place and the manager is an absolute idiot. Nobody should ever stay here, total waste of money.", random);
        flag(rude, "Offensive language", 1);
        rows.add(rude);

        reviewRepository.saveAll(rows);
    }

    private int pickRating(Random random) {
        int x = random.nextInt(100);
        return x < 45 ? 5 : x < 75 ? 4 : x < 90 ? 3 : 2;
    }

    private int nameIndex(Random random, Set<Integer> used) {
        int i;
        do {
            i = random.nextInt(NAMES.length);
        } while (!used.add(i));
        return i;
    }

    private String[] pick(Random random, String[][] pool) {
        return pool[random.nextInt(pool.length)];
    }

    private Review base(String type, String targetId, String targetName, int nameIdx, Random random) {
        Review r = new Review();
        r.setTargetType(type);
        r.setTargetId(targetId);
        r.setTargetName(targetName);
        r.setUserId(String.format("demo-reviewer-%02d", nameIdx + 1));
        r.setUserName(NAMES[nameIdx % NAMES.length]);
        r.setVerified(random.nextInt(100) < 60);
        return r;
    }

    private void fill(Review r, int rating, String title, String text, Random random) {
        Instant created = Instant.now().minus(Duration.ofHours(6 + random.nextInt(24 * 60)));
        r.setRating(rating);
        r.setTitle(title);
        r.setText(text);
        r.setCreatedAt(created);
        r.setUpdatedAt(created);
        int helpful = random.nextInt(100) < 70 ? random.nextInt(14) : 0;
        for (int i = 0; i < helpful; i++) {
            r.getHelpfulBy().add(String.format("demo-voter-%02d", i + 1));
        }
        r.setHelpfulCount(r.getHelpfulBy().size());
        if (random.nextInt(100) < 40) {
            int replies = 1 + random.nextInt(2);
            for (int i = 0; i < replies; i++) {
                int who = random.nextInt(NAMES.length);
                r.getReplies().add(new Review.Reply(UUID.randomUUID().toString().substring(0, 8),
                        String.format("demo-reviewer-%02d", who + 1), NAMES[who], REPLIES[random.nextInt(REPLIES.length)],
                        created.plus(Duration.ofHours(2 + random.nextInt(60)))));
            }
        }
    }

    private void flag(Review r, String reason, int flags) {
        for (int i = 0; i < flags; i++) {
            r.getFlags().add(new Review.Flag(String.format("demo-voter-%02d", i + 1), reason, "", Instant.now().minus(Duration.ofHours(3))));
        }
        r.setFlagCount(r.getFlags().size());
        r.setFlagsReviewed(false);
    }

    /** Three small sample pictures (gradients) so that the "with photos" filter has something to show. */
    private List<String> createPhotos() {
        Color[][] colors = {{new Color(30, 144, 255), new Color(255, 183, 77)}, {new Color(46, 139, 87), new Color(240, 230, 140)},
                {new Color(138, 43, 226), new Color(255, 160, 122)}};
        List<String> ids = new ArrayList<>();
        for (Color[] c : colors) {
            try {
                BufferedImage img = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setPaint(new GradientPaint(0, 0, c[0], 640, 400, c[1]));
                g.fillRect(0, 0, 640, 400);
                g.dispose();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", out);
                ReviewPhoto p = new ReviewPhoto();
                p.setUserId("demo-reviewer-01");
                p.setContentType("image/jpeg");
                p.setData(out.toByteArray());
                p.setCreatedAt(Instant.now());
                ids.add(photoRepository.save(p).getId());
            } catch (Throwable e) {
                // the sample pictures are optional
            }
        }
        return ids;
    }
}
