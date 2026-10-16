package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.PriceFreeze;
import com.makemytrip.makemytrip.services.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pricing")
@CrossOrigin(origins = "*")
public class PricingController {
    @Autowired
    private PricingService pricingService;

    private LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Date must look like 2026-12-31");
        }
    }

    @GetMapping("/flight/{id}")
    public PricingService.PriceQuote flight(@PathVariable String id, @RequestParam(required = false) String userId) {
        return pricingService.quoteFlightForUser(id, userId);
    }

    @GetMapping("/hotel/{id}")
    public PricingService.PriceQuote hotel(@PathVariable String id, @RequestParam(required = false) String stayDate,
                                           @RequestParam(required = false) String userId) {
        return pricingService.quoteHotelForUser(id, date(stayDate), userId);
    }

    @GetMapping("/hotel/{id}/calendar")
    public List<Map<String, Object>> calendar(@PathVariable String id, @RequestParam(defaultValue = "14") int days) {
        return pricingService.hotelCalendar(id, days);
    }

    @GetMapping("/history/{type}/{id}")
    public Map<String, Object> history(@PathVariable String type, @PathVariable String id,
                                       @RequestParam(defaultValue = "30") int days) {
        return pricingService.historyView(type.toUpperCase(), id, days);
    }

    @PostMapping("/freeze")
    public PriceFreeze freeze(@RequestParam String userId, @RequestParam String type, @RequestParam String itemId,
                              @RequestParam(defaultValue = "24") int hours, @RequestParam(required = false) String stayDate) {
        return pricingService.createFreeze(userId, type.toUpperCase(), itemId, hours, date(stayDate));
    }

    @GetMapping("/freezes")
    public List<PriceFreeze> freezes(@RequestParam String userId) {
        return pricingService.freezesOf(userId);
    }

    @DeleteMapping("/freeze/{id}")
    public PriceFreeze cancel(@PathVariable String id, @RequestParam String userId) {
        return pricingService.cancelFreeze(id, userId);
    }

    @GetMapping("/surges")
    public List<Map<String, Object>> surges(@RequestParam(defaultValue = "120") int days) {
        return pricingService.upcomingSurges(days);
    }
}
