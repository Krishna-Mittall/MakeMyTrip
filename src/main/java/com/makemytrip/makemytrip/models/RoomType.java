package com.makemytrip.makemytrip.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** One kind of room in a hotel (Standard / Deluxe / Suite) - stored inside the Hotel document. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomType {
    private String name;
    private String description;
    private String bedType;
    private int sizeSqFt;
    private int capacity;                 // guests
    private List<String> features = new ArrayList<>();
    private List<String> images = new ArrayList<>();
    private double priceMultiplier = 1.0; // relative to the hotel's price per night
    private int totalRooms;
    private int availableRooms;
    private boolean premium;              // upgraded room (shown as an upsell)
}
