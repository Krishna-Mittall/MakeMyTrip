package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.FlightTrack;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FlightTrackRepository extends MongoRepository<FlightTrack, String> {
    List<FlightTrack> findByUserId(String userId);

    List<FlightTrack> findByFlightId(String flightId);

    FlightTrack findByUserIdAndFlightId(String userId, String flightId);

    void deleteByUserIdAndFlightId(String userId, String flightId);

    void deleteByFlightId(String flightId);
}
