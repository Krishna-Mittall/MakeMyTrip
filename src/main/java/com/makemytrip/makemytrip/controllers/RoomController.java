package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Hotel;
import com.makemytrip.makemytrip.models.RoomType;
import com.makemytrip.makemytrip.repositories.HotelRepository;
import com.makemytrip.makemytrip.services.PricingService;
import com.makemytrip.makemytrip.services.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
@CrossOrigin(origins = "*")
public class RoomController {
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RoomService roomService;
    @Autowired
    private PricingService pricingService;

    /** Room-type grid of a hotel with the live price of every room type. */
    @GetMapping("/hotel/{hotelId}")
    public Map<String, Object> rooms(@PathVariable String hotelId,
                                     @RequestParam(required = false) String stayDate,
                                     @RequestParam(required = false) String userId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hotel not found"));
        roomService.ensureRoomTypes(hotel);
        LocalDate date = null;
        if (stayDate != null && !stayDate.isBlank()) {
            try {
                date = LocalDate.parse(stayDate);
            } catch (DateTimeParseException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "stayDate must look like 2026-12-31");
            }
        }
        PricingService.PriceQuote quote = pricingService.quoteHotelForUser(hotelId, date, userId);
        double unit = PricingService.effectivePrice(quote.getFreeze(), quote.getCurrentPrice());

        double standardPrice = 0;
        List<Map<String, Object>> list = new ArrayList<>();
        for (RoomType type : hotel.getRoomTypes()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", type.getName());
            m.put("description", type.getDescription());
            m.put("bedType", type.getBedType());
            m.put("sizeSqFt", type.getSizeSqFt());
            m.put("capacity", type.getCapacity());
            m.put("features", type.getFeatures());
            m.put("images", type.getImages());
            m.put("premium", type.isPremium());
            m.put("totalRooms", type.getTotalRooms());
            m.put("availableRooms", type.getAvailableRooms());
            m.put("priceMultiplier", type.getPriceMultiplier());
            double price = Math.round(unit * type.getPriceMultiplier());
            m.put("pricePerNight", price);
            if (standardPrice == 0) {
                standardPrice = price;
            }
            m.put("upgradeCost", Math.max(0, price - standardPrice));
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hotelId", hotelId);
        out.put("hotelName", hotel.gethotelName());
        out.put("stayDate", quote.getForDate());
        out.put("quote", quote);
        out.put("frozen", quote.getFreeze() != null);
        out.put("roomTypes", list);
        return out;
    }
}
