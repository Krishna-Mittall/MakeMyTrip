package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Flight;
import com.makemytrip.makemytrip.repositories.FlightRepository;
import com.makemytrip.makemytrip.services.AccessService;
import com.makemytrip.makemytrip.services.FlightStatusService;
import com.makemytrip.makemytrip.services.MockFlightApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The mock flight-data API. It answers like a real provider would, so the application reads it over the same
 * interface it would use for a real one. Admins can change what it reports for a flight (delay, cancel, move the clock).
 */
@RestController
@RequestMapping("/mock-api/flights")
@CrossOrigin(origins = "*")
public class MockFlightApiController {
    @Autowired
    private MockFlightApiService mockApi;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private AccessService accessService;
    @Autowired
    private FlightStatusService flightStatusService;

    @GetMapping("/{flightId}")
    public MockFlightApiService.Report report(@PathVariable String flightId) {
        Flight f = flightRepository.findById(flightId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flight not found"));
        return mockApi.fetch(f);
    }

    /** action: DELAY (minutes, reason), ON_TIME, CANCEL (reason), SHIFT (minutes: fast-forward the clock), CLEAR. */
    @PostMapping("/{flightId}/simulate")
    public Map<String, Object> simulate(@PathVariable String flightId, @RequestParam String adminId, @RequestParam String action,
                                        @RequestParam(required = false) Integer minutes, @RequestParam(required = false) String reason) {
        accessService.requireAdmin(adminId);
        Flight f = flightRepository.findById(flightId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flight not found"));
        try {
            mockApi.simulate(flightId, action, minutes, reason);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        flightStatusService.sync(f);
        return Map.of("flightId", flightId, "status", flightStatusService.get(flightId, null));
    }
}
