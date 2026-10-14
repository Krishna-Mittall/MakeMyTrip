package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.services.FlightStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Live flight status: one flight, the flights a user follows, and an airport style board. */
@RestController
@RequestMapping("/flight-status")
@CrossOrigin(origins = "*")
public class FlightStatusController {
    @Autowired
    private FlightStatusService flightStatusService;

    @GetMapping("/board")
    public List<FlightStatusService.StatusView> board(@RequestParam(defaultValue = "24") int hours,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String userId) {
        return flightStatusService.board(hours, q, userId);
    }

    @GetMapping("/tracked")
    public List<FlightStatusService.StatusView> tracked(@RequestParam String userId) {
        return flightStatusService.tracked(userId);
    }

    @GetMapping("/batch")
    public List<FlightStatusService.StatusView> batch(@RequestParam String ids, @RequestParam(required = false) String userId) {
        return flightStatusService.batch(Arrays.asList(ids.split(",")), userId);
    }

    @GetMapping("/{flightId}")
    public FlightStatusService.StatusView get(@PathVariable String flightId, @RequestParam(required = false) String userId) {
        return flightStatusService.get(flightId, userId);
    }

    @PostMapping("/track")
    public FlightStatusService.StatusView track(@RequestParam String userId, @RequestParam String flightId) {
        return flightStatusService.track(userId, flightId, "MANUAL", null);
    }

    @DeleteMapping("/track")
    public Map<String, Object> untrack(@RequestParam String userId, @RequestParam String flightId) {
        flightStatusService.untrack(userId, flightId);
        return Map.of("tracked", false);
    }
}
