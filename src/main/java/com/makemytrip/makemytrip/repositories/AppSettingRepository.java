package com.makemytrip.makemytrip.repositories;

import com.makemytrip.makemytrip.models.AppSetting;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppSettingRepository extends MongoRepository<AppSetting, String> {
}
