package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.Review;
import com.makemytrip.makemytrip.models.ReviewPhoto;
import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.repositories.ReviewPhotoRepository;
import com.makemytrip.makemytrip.repositories.ReviewRepository;
import com.makemytrip.makemytrip.repositories.UserRepository;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Ratings and reviews of hotels and flights: photos, replies, helpful votes, flagging and moderation. */
@Service
public class ReviewService {
    public static final List<String> FLAG_REASONS = List.of("Spam", "Offensive language", "Fake review", "Irrelevant", "Contains personal information", "Other");
    public static final int AUTO_HIDE_FLAGS = 3;
    private static final int MAX_PHOTOS = 4;
    private static final long MAX_PHOTO_BYTES = 1_500_000;

    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ReviewPhotoRepository photoRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private NotificationService notificationService;

    @Data
    @NoArgsConstructor
    public static class ReviewView {
        private String id;
        private String targetType;
        private String targetId;
        private String targetName;
        private String userName;
        private boolean verified;
        private int rating;
        private String title;
        private String text;
        private List<String> photos = new ArrayList<>();
        private int helpfulCount;
        private boolean helpfulByMe;
        private boolean flaggedByMe;
        private boolean mine;
        private String status;
        private int flagCount;
        private List<Review.Reply> replies = new ArrayList<>();
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ------------------------------------------------------------------ helpers

    private void checkTarget(String type, String id) {
        boolean exists = "HOTEL".equals(type) ? hotelRepository.existsById(id)
                : "FLIGHT".equals(type) && flightRepository.existsById(id);
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "The " + type.toLowerCase() + " you want to review was not found");
        }
    }

    private String nameOf(String type, String id) {
        if ("HOTEL".equals(type)) {
            return hotelRepository.findById(id).map(Hotel::gethotelName).orElse("");
        }
        return flightRepository.findById(id).map(f -> f.getFlightName() + " (" + f.getFrom() + " - " + f.getTo() + ")").orElse("");
    }

    private Users user(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        }
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in"));
    }

    private String displayName(Users u) {
        String last = u.getLastName() == null || u.getLastName().isBlank() ? "" : " " + u.getLastName().charAt(0) + ".";
        return (u.getFirstName() == null ? "Traveller" : u.getFirstName()) + last;
    }

    private ReviewView view(Review r, String viewerId, boolean moderator) {
        ReviewView v = new ReviewView();
        v.setId(r.getId());
        v.setTargetType(r.getTargetType());
        v.setTargetId(r.getTargetId());
        v.setTargetName(r.getTargetName());
        v.setUserName(r.getUserName());
        v.setVerified(r.isVerified());
        v.setRating(r.getRating());
        v.setTitle(r.getTitle());
        v.setText(r.getText());
        for (String photoId : r.getPhotoIds()) {
            v.getPhotos().add("/reviews/photos/" + photoId);
        }
        v.setHelpfulCount(r.getHelpfulCount());
        v.setHelpfulByMe(viewerId != null && r.getHelpfulBy().contains(viewerId));
        v.setFlaggedByMe(viewerId != null && r.getFlags().stream().anyMatch(f -> viewerId.equals(f.getUserId())));
        v.setMine(viewerId != null && viewerId.equals(r.getUserId()));
        v.setStatus(r.getStatus());
        v.setFlagCount(moderator ? r.getFlagCount() : 0);
        v.setReplies(r.getReplies());
        v.setCreatedAt(r.getCreatedAt());
        v.setUpdatedAt(r.getUpdatedAt());
        return v;
    }

    private boolean visible(Review r, String viewerId) {
        if ("PUBLISHED".equals(r.getStatus())) {
            return true;
        }
        return "HIDDEN".equals(r.getStatus()) && viewerId != null && viewerId.equals(r.getUserId());
    }

    // ------------------------------------------------------------------ list and summary

    public Map<String, Object> list(String type, String targetId, String sort, Integer rating, boolean withPhotos,
                                    boolean verifiedOnly, String viewerId) {
        List<Review> all = reviewRepository.findByTargetTypeAndTargetId(type, targetId).stream()
                .filter(r -> visible(r, viewerId)).collect(Collectors.toList());

        // the summary always covers all visible reviews, the filters only change the list
        Map<String, Object> summary = summary(all.stream().filter(r -> "PUBLISHED".equals(r.getStatus())).collect(Collectors.toList()));

        List<Review> list = all.stream()
                .filter(r -> rating == null || r.getRating() == rating)
                .filter(r -> !withPhotos || !r.getPhotoIds().isEmpty())
                .filter(r -> !verifiedOnly || r.isVerified())
                .collect(Collectors.toList());

        Comparator<Review> newest = Comparator.comparing(Review::getCreatedAt).reversed();
        String s = sort == null ? "helpful" : sort;
        switch (s) {
            case "newest":
                list.sort(newest);
                break;
            case "highest":
                list.sort(Comparator.comparingInt(Review::getRating).reversed().thenComparing(newest));
                break;
            case "lowest":
                list.sort(Comparator.comparingInt(Review::getRating).thenComparing(newest));
                break;
            default:
                list.sort(Comparator.comparingInt(Review::getHelpfulCount).reversed().thenComparing(newest));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("total", list.size());
        out.put("reviews", list.stream().map(r -> view(r, viewerId, false)).collect(Collectors.toList()));
        return out;
    }

    private Map<String, Object> summary(List<Review> reviews) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            distribution.put(String.valueOf(i), 0);
        }
        double sum = 0;
        for (Review r : reviews) {
            sum += r.getRating();
            distribution.merge(String.valueOf(r.getRating()), 1, Integer::sum);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("average", reviews.isEmpty() ? 0 : Math.round(sum / reviews.size() * 10.0) / 10.0);
        m.put("count", reviews.size());
        m.put("distribution", distribution);
        return m;
    }

    /** Average rating and number of reviews of every hotel / flight (shown on the search cards). */
    private Map<String, double[]> averages(String type) {
        Map<String, double[]> map = new HashMap<>();
        for (Review r : reviewRepository.findByTargetType(type)) {
            if (!"PUBLISHED".equals(r.getStatus())) {
                continue;
            }
            double[] a = map.computeIfAbsent(r.getTargetId(), k -> new double[2]);
            a[0] += r.getRating();
            a[1] += 1;
        }
        return map;
    }

    public void enrichHotels(List<Hotel> hotels) {
        Map<String, double[]> avg = averages("HOTEL");
        for (Hotel h : hotels) {
            double[] a = avg.get(h.getId());
            if (a != null && a[1] > 0) {
                h.setAvgRating(Math.round(a[0] / a[1] * 10.0) / 10.0);
                h.setReviewCount((int) a[1]);
            }
        }
    }

    public void enrichFlights(List<Flight> flights) {
        Map<String, double[]> avg = averages("FLIGHT");
        for (Flight f : flights) {
            double[] a = avg.get(f.getId());
            if (a != null && a[1] > 0) {
                f.setAvgRating(Math.round(a[0] / a[1] * 10.0) / 10.0);
                f.setReviewCount((int) a[1]);
            }
        }
    }

    // ------------------------------------------------------------------ writing reviews

    public ReviewView save(String userId, String type, String targetId, int rating, String title, String text, List<String> photoIds) {
        Users u = user(userId);
        if (!"HOTEL".equals(type) && !"FLIGHT".equals(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "targetType must be HOTEL or FLIGHT");
        }
        checkTarget(type, targetId);
        if (rating < 1 || rating > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a rating from 1 to 5 stars");
        }
        String cleanText = text == null ? "" : text.trim();
        if (cleanText.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please write a few words about your experience (at least 10 characters)");
        }
        if (cleanText.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The review can have at most 2000 characters");
        }
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The title can have at most 100 characters");
        }
        List<String> photos = photoIds == null ? new ArrayList<>() : photoIds.stream().filter(p -> !p.isBlank()).collect(Collectors.toList());
        if (photos.size() > MAX_PHOTOS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can add at most " + MAX_PHOTOS + " photos");
        }
        for (String photoId : photos) {
            if (!photoRepository.existsById(photoId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "One of the photos could not be found, please upload it again");
            }
        }

        Instant now = Instant.now();
        Review r = reviewRepository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId).orElse(null);
        if (r == null) {
            r = new Review();
            r.setUserId(userId);
            r.setTargetType(type);
            r.setTargetId(targetId);
            r.setCreatedAt(now);
        } else if ("REMOVED".equals(r.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your earlier review was removed by a moderator");
        }
        r.setTargetName(nameOf(type, targetId));
        r.setUserName(displayName(u));
        r.setVerified(u.getBookings() != null && u.getBookings().stream().anyMatch(b -> targetId.equals(b.getBookingId())));
        r.setRating(rating);
        r.setTitle(cleanTitle);
        r.setText(cleanText);
        r.setPhotoIds(photos);
        r.setUpdatedAt(now);
        r = reviewRepository.save(r);
        return view(r, userId, false);
    }

    public void delete(String reviewId, String userId) {
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review not found"));
        if (!userId.equals(r.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only delete your own reviews");
        }
        reviewRepository.delete(r);
    }

    public Map<String, Object> toggleHelpful(String reviewId, String userId) {
        user(userId);
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review not found"));
        if (userId.equals(r.getUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot vote on your own review");
        }
        boolean on;
        if (r.getHelpfulBy().contains(userId)) {
            r.getHelpfulBy().remove(userId);
            on = false;
        } else {
            r.getHelpfulBy().add(userId);
            on = true;
        }
        r.setHelpfulCount(r.getHelpfulBy().size());
        reviewRepository.save(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("helpfulCount", r.getHelpfulCount());
        m.put("helpfulByMe", on);
        return m;
    }

    public ReviewView reply(String reviewId, String userId, String text) {
        Users u = user(userId);
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review not found"));
        if (!"PUBLISHED".equals(r.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot reply to this review");
        }
        String clean = text == null ? "" : text.trim();
        if (clean.length() < 2 || clean.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reply needs 2 to 500 characters");
        }
        Review.Reply reply = new Review.Reply(UUID.randomUUID().toString().substring(0, 8), userId, displayName(u), clean, Instant.now());
        r.getReplies().add(reply);
        reviewRepository.save(r);
        if (!userId.equals(r.getUserId())) {
            notificationService.notifyUser(r.getUserId(), "REVIEW", "New reply to your review",
                    displayName(u) + " replied to your review of " + r.getTargetName() + ".",
                    "HOTEL".equals(r.getTargetType()) ? "/book-hotel/" + r.getTargetId() : "/book-flight/" + r.getTargetId());
        }
        return view(r, userId, false);
    }

    public Map<String, Object> flag(String reviewId, String userId, String reason, String details) {
        user(userId);
        if (reason == null || !FLAG_REASONS.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select why this review is inappropriate");
        }
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review not found"));
        if (userId.equals(r.getUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot flag your own review");
        }
        if (r.getFlags().stream().anyMatch(f -> userId.equals(f.getUserId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already flagged this review");
        }
        r.getFlags().add(new Review.Flag(userId, reason, details == null ? "" : (details.length() > 300 ? details.substring(0, 300) : details), Instant.now()));
        r.setFlagCount(r.getFlags().size());
        r.setFlagsReviewed(false);
        if (r.getFlagCount() >= AUTO_HIDE_FLAGS && "PUBLISHED".equals(r.getStatus())) {
            r.setStatus("HIDDEN");   // hidden until a moderator has decided
        }
        reviewRepository.save(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", "Thanks, a moderator will review this.");
        m.put("hidden", "HIDDEN".equals(r.getStatus()));
        return m;
    }

    // ------------------------------------------------------------------ photos

    public String savePhoto(String userId, String contentType, byte[] data) {
        user(userId);
        if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/webp"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only JPG, PNG or WebP photos are allowed");
        }
        if (data == null || data.length == 0 || data.length > MAX_PHOTO_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Each photo must be smaller than 1.5 MB");
        }
        ReviewPhoto p = new ReviewPhoto();
        p.setUserId(userId);
        p.setContentType(contentType);
        p.setData(data);
        p.setCreatedAt(Instant.now());
        return photoRepository.save(p).getId();
    }

    public ReviewPhoto photo(String id) {
        return photoRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo not found"));
    }

    // ------------------------------------------------------------------ moderation

    public List<Map<String, Object>> moderationQueue(String status) {
        List<Review> flagged = reviewRepository.findByFlagCountGreaterThanOrderByCreatedAtDesc(0);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Review r : flagged) {
            boolean pending = !r.isFlagsReviewed() && !"REMOVED".equals(r.getStatus());
            if ("PENDING".equalsIgnoreCase(status) && !pending) {
                continue;
            }
            if ("REMOVED".equalsIgnoreCase(status) && !"REMOVED".equals(r.getStatus())) {
                continue;
            }
            if ("KEPT".equalsIgnoreCase(status) && !(r.isFlagsReviewed() && !"REMOVED".equals(r.getStatus()))) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("review", view(r, null, true));
            m.put("flags", r.getFlags());
            m.put("moderationNote", r.getModerationNote());
            m.put("moderatedBy", r.getModeratedBy());
            m.put("moderatedAt", r.getModeratedAt());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> moderationStats() {
        long pending = 0, removed = 0, kept = 0;
        for (Review r : reviewRepository.findByFlagCountGreaterThanOrderByCreatedAtDesc(0)) {
            if ("REMOVED".equals(r.getStatus())) {
                removed++;
            } else if (r.isFlagsReviewed()) {
                kept++;
            } else {
                pending++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pending);
        m.put("removed", removed);
        m.put("kept", kept);
        return m;
    }

    /** A moderator keeps the review: the flags are cleared and the review is visible again. */
    public Map<String, Object> dismiss(String reviewId, Users moderator, String note) {
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review not found"));
        r.setStatus("PUBLISHED");
        r.setFlagsReviewed(true);
        r.setModerationNote(note);
        r.setModeratedBy(displayName(moderator));
        r.setModeratedAt(Instant.now());
        reviewRepository.save(r);
        return Map.of("status", r.getStatus());
    }

    /** A moderator removes the review. */
    public Map<String, Object> remove(String reviewId, Users moderator, String note) {
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Review not found"));
        r.setStatus("REMOVED");
        r.setFlagsReviewed(true);
        r.setModerationNote(note);
        r.setModeratedBy(displayName(moderator));
        r.setModeratedAt(Instant.now());
        reviewRepository.save(r);
        notificationService.notifyUser(r.getUserId(), "REVIEW", "Your review was removed",
                "A moderator removed your review of " + r.getTargetName() + " because it broke our community guidelines.", "/profile");
        return Map.of("status", r.getStatus());
    }
}
