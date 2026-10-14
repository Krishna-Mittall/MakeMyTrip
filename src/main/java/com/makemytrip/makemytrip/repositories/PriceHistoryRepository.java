package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.PriceHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface PriceHistoryRepository extends MongoRepository<PriceHistory, String> {
    List<PriceHistory> findByItemTypeAndItemIdAndTimestampAfterOrderByTimestampAsc(String itemType, String itemId, Instant after);

    List<PriceHistory> findByItemTypeAndItemIdOrderByTimestampAsc(String itemType, String itemId);

    PriceHistory findFirstByItemTypeAndItemIdOrderByTimestampDesc(String itemType, String itemId);

    PriceHistory findFirstByItemTypeAndItemIdAndTimestampBeforeOrderByTimestampDesc(String itemType, String itemId, Instant before);

    long countByItemTypeAndItemId(String itemType, String itemId);
}
