package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.PriceFreeze;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PriceFreezeRepository extends MongoRepository<PriceFreeze, String> {
    List<PriceFreeze> findByUserIdOrderByCreatedAtDesc(String userId);

    List<PriceFreeze> findByUserIdAndStatus(String userId, String status);

    List<PriceFreeze> findByStatus(String status);
}
