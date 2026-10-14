package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.MockFlightOverride;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MockFlightOverrideRepository extends MongoRepository<MockFlightOverride, String> {
}
