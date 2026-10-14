package com.makemytrip.makemytrip.models;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "hotels")
public class Hotel {
    @Id
    private String _id;
    private String hotelName;
    private String location;
    private double pricePerNight;
    private int availableRooms;
    private String amenities;
    @Getter @Setter private List<String> images = new ArrayList<>();
    @Getter @Setter private List<String> tags = new ArrayList<>();          // beach, mountain, heritage ...
    @Getter @Setter private List<RoomType> roomTypes = new ArrayList<>();  // Standard / Deluxe / Suite

    // values worked out when the hotel is requested - they are not stored in the database
    @Transient @Getter @Setter private double currentPricePerNight;  // price after the dynamic pricing engine
    @Transient @Getter @Setter private String priceTrend;            // UP / DOWN / STABLE
    @Transient @Getter @Setter private double priceChangePercent;
    @Transient @Getter @Setter private double avgRating;
    @Transient @Getter @Setter private int reviewCount;
    // Getters and Setters
    public String getId() {
        return _id;
    }

    public void setId(String id) {
        this._id = id;
    }

    public void setamenities(String amenities) {
        this.amenities = amenities;
    }

    public String getamenities() {
        return amenities;
    }

    public String gethotelName() {
        return hotelName;
    }

    public void sethotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getAvailableRooms() {
        return availableRooms;
    }

    public void setAvailableRooms(int availableRooms) {
        this.availableRooms = availableRooms;
    }

    public double getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(double pricePerNight) {
        this.pricePerNight = pricePerNight;
    }
}