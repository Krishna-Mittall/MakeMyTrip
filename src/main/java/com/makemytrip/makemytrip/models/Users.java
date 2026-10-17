package com.makemytrip.makemytrip.models;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.ArrayList;
@Document(collection = "users")
public class Users {
    @Id
    private String _id;
    private String firstName;
    private String lastName;
    private String email;
    // the password is accepted on signup/login but never sent back to the browser
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String role;
    private String phoneNumber;
    private List<Booking> bookings = new ArrayList<>();;
    private Preferences preferences = new Preferences();


    public String getFirstName() {return firstName;}
    public String getId() {
        return _id;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public String getPassword() {return password;}
    public String getEmail() {return email;}
    public void setEmail(String email) {this.email = email;}
    public String getRole() {return role;}
    public void setPassword(String password) {this.password = password;}
    public void setRole(String role) {this.role = role;}
    public List<Booking> getBookings(){return bookings;}
    public void setBookings(List<Booking> bookings){this.bookings=bookings;}
    public Preferences getPreferences(){return preferences;}
    public void setPreferences(Preferences preferences){this.preferences=preferences;}

    /** Saved seat / room preferences, re-used on the next booking. */
    @Getter
    @Setter
    public static class Preferences {
        private String seatPosition = "ANY";   // WINDOW / AISLE / MIDDLE / ANY
        private String seatTier = "ANY";       // STANDARD / EXTRA_LEGROOM / PREMIUM / ANY
        private String roomType = "";          // Standard / Deluxe / Suite
        private String updatedAt;
    }

    public static class Booking{
        private String type;
        private String bookingId;
        private String date;
        private int quantity;
        private double totalPrice;

        // Getters and Setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBookingId() {
            return bookingId;
        }

        public void setBookingId(String bookingId) {
            this.bookingId = bookingId;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(double totalPrice) {
            this.totalPrice = totalPrice;
        }

        // ---- extra details used by cancellation, seat/room selection and live pricing ----
        @Getter @Setter private String reference;        // unique booking reference shown to the user
        @Getter @Setter private String status = "CONFIRMED"; // CONFIRMED / PARTIALLY_CANCELLED / CANCELLED
        @Getter @Setter private String createdAt;        // when the booking was made (ISO instant)
        @Getter @Setter private String travelDate;       // flight departure / hotel check-in (ISO instant)
        @Getter @Setter private String title;            // flight name / hotel name
        @Getter @Setter private String subtitle;         // route / location
        @Getter @Setter private int nights;              // hotels only
        @Getter @Setter private double unitPrice;        // price per seat / room-night that was charged
        @Getter @Setter private double seatCharges;      // premium seat upgrades
        @Getter @Setter private List<String> seats = new ArrayList<>();
        @Getter @Setter private String roomType;
        @Getter @Setter private int cancelledQuantity;
        @Getter @Setter private double refundedAmount;
        @Getter @Setter private String promoCode;        // promo code used for this booking
        @Getter @Setter private double promoDiscount;    // amount taken off by the promo code
    }
}