package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.Refund;
import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.models.Users.Booking;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.repositories.RefundRepository;
import com.makemytrip.makemytrip.repositories.UserRepository;
import com.makemytrip.makemytrip.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Cancellation of bookings (fully or partly), automatic refund calculation and the refund status tracker. */
@Service
public class CancellationService {
    public static final List<String> REASONS = List.of(
            "Change of plans",
            "Found a better price",
            "Medical emergency",
            "Flight or hotel schedule changed",
            "Booked by mistake",
            "Travel restrictions or visa issue",
            "Personal or family reason",
            "Other");
    private static final String REFERENCE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** How long a refund stays in PENDING / PROCESSED. Short values are used so the tracker can be tried out quickly. */
    @Value("${app.refund.processing-minutes:2}")
    private int processingMinutes;
    @Value("${app.refund.completion-minutes:6}")
    private int completionMinutes;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private SeatService seatService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private PricingService pricingService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private EventHub eventHub;

    /** Everything that is worked out before a booking is cancelled. */
    public static class Plan {
        public Users user;
        public Booking booking;
        public boolean flight;
        public int quantity;
        public List<String> seats = new ArrayList<>();
        public long hoursToTravel;
        public int percent;
        public String policyText;
        public double originalAmount;
        public double refundAmount;
    }

    // ------------------------------------------------------------------ planning

    private Instant travelInstant(Booking b) {
        Instant travel = TimeUtil.parse(b.getTravelDate());
        if (travel != null) {
            return travel;
        }
        if ("Flight".equals(b.getType())) {
            Optional<Flight> f = flightRepository.findById(b.getBookingId());
            if (f.isPresent() && TimeUtil.parse(f.get().getDepartureTime()) != null) {
                return TimeUtil.parse(f.get().getDepartureTime());
            }
        }
        // old bookings without a travel date: treat them as far in the future
        return Instant.now().plus(Duration.ofDays(7));
    }

    private double share(Booking b, int qty, List<String> seats) {
        if ("Flight".equals(b.getType())) {
            double base = Math.max(0, b.getTotalPrice() - b.getSeatCharges());
            double share = base / Math.max(1, b.getQuantity()) * qty;
            for (String seat : seats) {
                share += seatService.chargeOfSeat(b.getBookingId(), seat);
            }
            return share;
        }
        return b.getTotalPrice() * qty / Math.max(1, b.getQuantity());
    }

    public Plan plan(String userId, String reference, List<String> seats, Integer quantity) {
        Users user = userRepository.findByBookingsReference(reference);
        if (user == null || !user.getId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        Booking b = user.getBookings().stream().filter(x -> reference.equals(x.getReference())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        int active = b.getQuantity() - b.getCancelledQuantity();
        if ("CANCELLED".equals(b.getStatus()) || active <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "This booking has already been cancelled");
        }
        Plan plan = new Plan();
        plan.user = user;
        plan.booking = b;
        plan.flight = "Flight".equals(b.getType());

        List<String> activeSeats = b.getSeats() == null ? new ArrayList<>() : b.getSeats();
        if (plan.flight && !activeSeats.isEmpty()) {
            if (seats != null && !seats.isEmpty()) {
                for (String raw : seats) {
                    String seat = raw.trim().toUpperCase();
                    if (!activeSeats.contains(seat)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Seat " + seat + " is not part of this booking");
                    }
                    if (!plan.seats.contains(seat)) {
                        plan.seats.add(seat);
                    }
                }
            } else if (quantity != null) {
                if (quantity < 1 || quantity > active) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Choose between 1 and " + active + " seats");
                }
                plan.seats.addAll(activeSeats.subList(activeSeats.size() - quantity, activeSeats.size()));
            } else {
                plan.seats.addAll(activeSeats);
            }
            plan.quantity = plan.seats.size();
        } else {
            int qty = quantity == null ? active : quantity;
            if (qty < 1 || qty > active) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Choose between 1 and " + active + (plan.flight ? " tickets" : " rooms"));
            }
            plan.quantity = qty;
        }

        long minutes = Duration.between(Instant.now(), travelInstant(b)).toMinutes();
        if (minutes < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking can no longer be cancelled because the "
                    + (plan.flight ? "flight has already departed" : "check-in time has passed"));
        }
        plan.hoursToTravel = minutes / 60;
        RefundPolicy.Result result = RefundPolicy.evaluate(plan.flight, plan.hoursToTravel);
        plan.percent = result.percent;
        plan.policyText = result.text;
        plan.originalAmount = Math.round(share(b, plan.quantity, plan.seats));
        plan.refundAmount = Math.round(plan.originalAmount * plan.percent / 100.0);
        return plan;
    }

    public Map<String, Object> preview(String userId, String reference, List<String> seats, Integer quantity) {
        Plan p = plan(userId, reference, seats, quantity);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reference", reference);
        m.put("type", p.booking.getType());
        m.put("quantity", p.quantity);
        m.put("seats", p.seats);
        m.put("hoursToTravel", p.hoursToTravel);
        m.put("originalAmount", p.originalAmount);
        m.put("refundPercent", p.percent);
        m.put("refundAmount", p.refundAmount);
        m.put("deduction", p.originalAmount - p.refundAmount);
        m.put("policyApplied", p.policyText);
        m.put("fullCancellation", p.quantity >= p.booking.getQuantity() - p.booking.getCancelledQuantity());
        m.put("expectedProcessedBy", Instant.now().plus(Duration.ofMinutes(processingMinutes)).toString());
        m.put("expectedCompletedBy", Instant.now().plus(Duration.ofMinutes(completionMinutes)).toString());
        m.put("policyTable", RefundPolicy.table(p.flight));
        return m;
    }

    // ------------------------------------------------------------------ cancelling

    public synchronized Refund cancel(String userId, String reference, List<String> seats, Integer quantity,
                                      String reason, String comments) {
        if (reason == null || !REASONS.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select a reason for the cancellation");
        }
        if (comments != null && comments.length() > 300) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Comments can have at most 300 characters");
        }
        Plan p = plan(userId, reference, seats, quantity);
        Booking b = p.booking;

        // give the seats / rooms back to the inventory
        if (p.flight) {
            Optional<Flight> flight = flightRepository.findById(b.getBookingId());
            if (flight.isPresent()) {
                Flight f = flight.get();
                f.setAvailableSeats(f.getAvailableSeats() + p.quantity);
                flightRepository.save(f);
                seatService.release(f.getId(), p.seats);
                pricingService.publishPrice(PricingService.FLIGHT, f.getId());
            }
        } else {
            Optional<Hotel> hotel = hotelRepository.findById(b.getBookingId());
            if (hotel.isPresent()) {
                Hotel h = hotel.get();
                h.setAvailableRooms(h.getAvailableRooms() + p.quantity);
                roomService.returnRooms(h, b.getRoomType(), p.quantity);
                hotelRepository.save(h);
                eventHub.publish("ROOM_UPDATE", Map.of("hotelId", h.getId()));
                pricingService.publishPrice(PricingService.HOTEL, h.getId());
            }
        }

        // update the booking
        b.setCancelledQuantity(b.getCancelledQuantity() + p.quantity);
        b.getSeats().removeAll(p.seats);
        b.setRefundedAmount(b.getRefundedAmount() + p.refundAmount);
        b.setStatus(b.getCancelledQuantity() >= b.getQuantity() ? "CANCELLED" : "PARTIALLY_CANCELLED");
        userRepository.save(p.user);

        // create the refund
        Instant now = Instant.now();
        Refund r = new Refund();
        r.setRefundReference(newReference());
        r.setBookingReference(reference);
        r.setUserId(userId);
        r.setUserName((p.user.getFirstName() == null ? "" : p.user.getFirstName()) + " " + (p.user.getLastName() == null ? "" : p.user.getLastName()));
        r.setBookingType(b.getType());
        r.setTitle(b.getTitle());
        r.setSubtitle(b.getSubtitle());
        r.setQuantityCancelled(p.quantity);
        r.setCancelledSeats(new ArrayList<>(p.seats));
        r.setOriginalAmount(p.originalAmount);
        r.setRefundPercent(p.percent);
        r.setRefundAmount(p.refundAmount);
        r.setDeduction(p.originalAmount - p.refundAmount);
        r.setPolicyApplied(p.policyText);
        r.setReason(reason);
        r.setComments(comments);
        r.setCreatedAt(now);
        r.setExpectedProcessedBy(now.plus(Duration.ofMinutes(processingMinutes)));
        r.setExpectedCompletedBy(now.plus(Duration.ofMinutes(completionMinutes)));
        r.setTimelineNote("Refunds are normally processed within 24 hours and credited to the original payment method in 5-7 business days. "
                + "In this demo the steps are sped up.");
        if (p.refundAmount <= 0) {
            r.setStatus("COMPLETED");
            r.setCompletedAt(now);
            r.getTimeline().add(new Refund.TimelineEntry("COMPLETED", now, "No refund is due for this cancellation under the policy"));
        } else {
            r.setStatus("PENDING");
            r.getTimeline().add(new Refund.TimelineEntry("PENDING", now, "Refund request received"));
        }
        r = refundRepository.save(r);

        String text = p.refundAmount > 0
                ? "Refund of ₹" + Math.round(p.refundAmount) + " for booking " + reference + " has been initiated."
                : "Booking " + reference + " was cancelled. No refund is due under the policy.";
        notificationService.notifyUser(userId, "REFUND", "Cancellation confirmed", text, "/profile");
        eventHub.publishToUser(userId, "REFUND_UPDATE", Map.of("refundId", r.getId(), "status", r.getStatus()));
        return r;
    }

    // ------------------------------------------------------------------ tracker

    public List<Refund> refundsOf(String userId) {
        return refundRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Refund get(String id, String userId) {
        Refund r = refundRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Refund not found"));
        if (userId != null && !userId.equals(r.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This refund belongs to another user");
        }
        return r;
    }

    public synchronized Refund advanceTo(Refund r, String status) {
        Instant now = Instant.now();
        if ("PROCESSED".equals(status)) {
            r.setStatus("PROCESSED");
            r.setProcessedAt(now);
            r.getTimeline().add(new Refund.TimelineEntry("PROCESSED", now, "Refund approved and sent to your bank / payment provider"));
        } else if ("COMPLETED".equals(status)) {
            r.setStatus("COMPLETED");
            r.setCompletedAt(now);
            if (r.getProcessedAt() == null) {
                r.setProcessedAt(now);
            }
            r.getTimeline().add(new Refund.TimelineEntry("COMPLETED", now, "Refund credited to your original payment method"));
        }
        r = refundRepository.save(r);
        String message = "PROCESSED".equals(status)
                ? "Your refund of ₹" + Math.round(r.getRefundAmount()) + " for booking " + r.getBookingReference() + " has been processed."
                : "Your refund of ₹" + Math.round(r.getRefundAmount()) + " for booking " + r.getBookingReference() + " is complete.";
        notificationService.notifyUser(r.getUserId(), "REFUND", "Refund " + status.toLowerCase(), message, "/profile");
        eventHub.publishToUser(r.getUserId(), "REFUND_UPDATE", Map.of("refundId", r.getId(), "status", r.getStatus()));
        return r;
    }

    /** Moves refunds from PENDING to PROCESSED to COMPLETED when their expected time has been reached. */
    @Scheduled(fixedDelay = 15000, initialDelay = 20000)
    public void progress() {
        Instant now = Instant.now();
        for (Refund r : refundRepository.findByStatus("PENDING")) {
            if (!now.isBefore(r.getExpectedProcessedBy())) {
                advanceTo(r, "PROCESSED");
            }
        }
        for (Refund r : refundRepository.findByStatus("PROCESSED")) {
            if (!now.isBefore(r.getExpectedCompletedBy())) {
                advanceTo(r, "COMPLETED");
            }
        }
    }

    // ------------------------------------------------------------------ admin

    public List<Refund> all(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return refundRepository.findAllByOrderByCreatedAtDesc();
        }
        List<Refund> list = refundRepository.findByStatus(status.toUpperCase());
        list.sort(Comparator.comparing(Refund::getCreatedAt).reversed());
        return list;
    }

    public Refund adminAdvance(String refundId) {
        Refund r = refundRepository.findById(refundId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Refund not found"));
        if ("PENDING".equals(r.getStatus())) {
            return advanceTo(r, "PROCESSED");
        }
        if ("PROCESSED".equals(r.getStatus())) {
            return advanceTo(r, "COMPLETED");
        }
        throw new ApiException(HttpStatus.CONFLICT, "This refund is already completed");
    }

    /** Numbers for the admin dashboard: why do people cancel, how much is refunded. */
    public Map<String, Object> analytics() {
        List<Refund> all = refundRepository.findAll();
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (String reason : REASONS) {
            reasons.put(reason, 0L);
        }
        Map<String, Long> status = new LinkedHashMap<>();
        status.put("PENDING", 0L);
        status.put("PROCESSED", 0L);
        status.put("COMPLETED", 0L);
        Map<String, Long> types = new LinkedHashMap<>();
        double refunded = 0;
        double percentSum = 0;
        for (Refund r : all) {
            reasons.merge(r.getReason() == null ? "Other" : r.getReason(), 1L, Long::sum);
            status.merge(r.getStatus(), 1L, Long::sum);
            types.merge(r.getBookingType() == null ? "Other" : r.getBookingType(), 1L, Long::sum);
            refunded += r.getRefundAmount();
            percentSum += r.getRefundPercent();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRequests", all.size());
        out.put("totalRefunded", refunded);
        out.put("averageRefundPercent", all.isEmpty() ? 0 : Math.round(percentSum / all.size()));
        out.put("byReason", reasons);
        out.put("byStatus", status);
        out.put("byType", types);
        return out;
    }

    private String newReference() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("RFD");
        for (int i = 0; i < 6; i++) {
            sb.append(REFERENCE_CHARS.charAt(random.nextInt(REFERENCE_CHARS.length())));
        }
        return sb.toString();
    }
}
