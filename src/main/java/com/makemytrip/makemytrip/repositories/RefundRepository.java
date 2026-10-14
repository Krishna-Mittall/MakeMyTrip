package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.Refund;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RefundRepository extends MongoRepository<Refund, String> {
    List<Refund> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Refund> findByStatus(String status);

    List<Refund> findAllByOrderByCreatedAtDesc();
}
