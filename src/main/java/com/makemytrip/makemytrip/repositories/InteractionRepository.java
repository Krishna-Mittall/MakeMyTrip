package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.Interaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface InteractionRepository extends MongoRepository<Interaction, String> {
    List<Interaction> findByUserId(String userId);

    boolean existsByUserIdAndItemTypeAndItemIdAndTypeAndCreatedAtAfter(String userId, String itemType, String itemId, String type, Instant after);
}
