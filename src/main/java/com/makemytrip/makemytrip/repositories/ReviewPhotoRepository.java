package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.ReviewPhoto;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewPhotoRepository extends MongoRepository<ReviewPhoto, String> {
}
