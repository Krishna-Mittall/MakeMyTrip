package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.RoomType;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Room types (Standard / Deluxe / Suite) of a hotel. They are created the first time a hotel is opened. */
@Service
public class RoomService {
    @Autowired
    private HotelRepository hotelRepository;

    private static final String[] STANDARD_IMAGES = {
            "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=800"};
    private static final String[] DELUXE_IMAGES = {
            "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1587474260584-136574528ed5?auto=format&fit=crop&w=800"};
    private static final String[] SUITE_IMAGES = {
            "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800",
            "https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=800"};

    private RoomType make(String name, String description, String bed, int size, int capacity, double multiplier,
                          boolean premium, String[] images, String... features) {
        RoomType type = new RoomType();
        type.setName(name);
        type.setDescription(description);
        type.setBedType(bed);
        type.setSizeSqFt(size);
        type.setCapacity(capacity);
        type.setPriceMultiplier(multiplier);
        type.setPremium(premium);
        type.setImages(new ArrayList<>(Arrays.asList(images)));
        type.setFeatures(new ArrayList<>(Arrays.asList(features)));
        return type;
    }

    /** Creates the default room types if the hotel has none and keeps the counts in line with availableRooms. */
    public synchronized Hotel ensureRoomTypes(Hotel hotel) {
        boolean changed = false;
        if (hotel.getRoomTypes() == null || hotel.getRoomTypes().isEmpty()) {
            int total = Math.max(hotel.getAvailableRooms(), 0);
            int suites = total >= 4 ? Math.max(1, Math.round(total * 0.15f)) : 0;
            int deluxe = total >= 3 ? Math.max(1, Math.round(total * 0.30f)) : 0;
            int standard = total - suites - deluxe;

            List<RoomType> types = new ArrayList<>();
            RoomType std = make("Standard", "Comfortable room with everything you need for a short stay.", "Queen bed", 280, 2, 1.0, false,
                    STANDARD_IMAGES, "Free Wi-Fi", "Air conditioning", "Smart TV", "Daily housekeeping");
            std.setTotalRooms(standard);
            std.setAvailableRooms(standard);
            types.add(std);
            if (deluxe > 0) {
                RoomType dlx = make("Deluxe", "Larger room with a work desk, a better view and breakfast included.", "King bed", 380, 3, 1.35, true,
                        DELUXE_IMAGES, "Garden / city view", "Work desk", "Mini bar", "Breakfast included");
                dlx.setTotalRooms(deluxe);
                dlx.setAvailableRooms(deluxe);
                types.add(dlx);
            }
            if (suites > 0) {
                RoomType suite = make("Suite", "Spacious suite with a separate living area and a private balcony.", "King bed + sofa bed", 620, 4, 2.0, true,
                        SUITE_IMAGES, "Private balcony", "Living area", "Bathtub", "Lounge access", "Breakfast included");
                suite.setTotalRooms(suites);
                suite.setAvailableRooms(suites);
                types.add(suite);
            }
            hotel.setRoomTypes(types);
            changed = true;
        } else {
            // keep the room types in line when an admin edits the number of rooms of the hotel
            int sum = hotel.getRoomTypes().stream().mapToInt(RoomType::getAvailableRooms).sum();
            int delta = hotel.getAvailableRooms() - sum;
            if (delta != 0) {
                RoomType first = hotel.getRoomTypes().get(0);
                int newAvailable = Math.max(0, first.getAvailableRooms() + delta);
                first.setTotalRooms(Math.max(first.getTotalRooms() + (newAvailable - first.getAvailableRooms()), newAvailable));
                first.setAvailableRooms(newAvailable);
                changed = true;
            }
        }
        if (changed) {
            hotelRepository.save(hotel);
        }
        return hotel;
    }

    public int totalRooms(Hotel hotel) {
        ensureRoomTypes(hotel);
        return hotel.getRoomTypes().stream().mapToInt(RoomType::getTotalRooms).sum();
    }

    public RoomType find(Hotel hotel, String name) {
        ensureRoomTypes(hotel);
        for (RoomType type : hotel.getRoomTypes()) {
            if (type.getName().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown room type " + name);
    }

    /** Takes rooms out of the inventory of a room type. The caller saves the hotel. */
    public void takeRooms(Hotel hotel, String typeName, int rooms) {
        RoomType type = find(hotel, typeName);
        if (type.getAvailableRooms() < rooms) {
            throw new ApiException(HttpStatus.CONFLICT, "Only " + type.getAvailableRooms() + " " + type.getName() + " room(s) left");
        }
        type.setAvailableRooms(type.getAvailableRooms() - rooms);
    }

    /** Gives rooms back (cancellation). The caller saves the hotel. */
    public void returnRooms(Hotel hotel, String typeName, int rooms) {
        if (typeName == null || typeName.isBlank()) {
            return;
        }
        RoomType type = find(hotel, typeName);
        type.setAvailableRooms(Math.min(type.getTotalRooms(), type.getAvailableRooms() + rooms));
    }
}
