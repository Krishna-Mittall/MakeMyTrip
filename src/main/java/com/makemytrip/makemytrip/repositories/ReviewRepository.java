package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.Review;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {
    List<Review> findByTargetTypeAndTargetId(String targetType, String targetId);

    List<Review> findByTargetType(String targetType);

    Optional<Review> findByUserIdAndTargetTypeAndTargetId(String userId, String targetType, String targetId);

    List<Review> findByFlagCountGreaterThanOrderByCreatedAtDesc(int flagCount);

    List<Review> findByUserId(String userId);
}
