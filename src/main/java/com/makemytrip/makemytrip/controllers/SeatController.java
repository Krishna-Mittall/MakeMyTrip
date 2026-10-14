package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.services.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/seats")
@CrossOrigin(origins = "*")
public class SeatController {
    @Autowired
    private SeatService seatService;
    @Autowired
    private FlightRepository flightRepository;

    private Flight flight(String id) {
        return flightRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flight not found"));
    }

    @GetMapping("/flight/{flightId}")
    public SeatService.SeatMapView seatMap(@PathVariable String flightId, @RequestParam(required = false) String userId) {
        return seatService.view(flight(flightId), userId);
    }

    @PostMapping("/hold")
    public SeatService.SeatMapView hold(@RequestParam String flightId, @RequestParam String userId, @RequestParam String seats) {
        List<String> list = new ArrayList<>();
        for (String s : seats.split(",")) {
            if (!s.isBlank()) {
                list.add(s.trim());
            }
        }
        return seatService.hold(flight(flightId), userId, list);
    }

    @PostMapping("/release")
    public SeatService.SeatMapView release(@RequestParam String flightId, @RequestParam String userId) {
        return seatService.releaseHolds(flight(flightId), userId);
    }
}
