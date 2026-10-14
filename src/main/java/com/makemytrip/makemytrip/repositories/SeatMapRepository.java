package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.SeatMap;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeatMapRepository extends MongoRepository<SeatMap, String> {
}
