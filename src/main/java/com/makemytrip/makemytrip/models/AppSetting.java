package com.makemytrip.makemytrip.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Small key / value settings that must survive a restart (for example the push (VAPID) keys). */
@Data
@NoArgsConstructor
@Document(collection = "app_settings")
public class AppSetting {
    @Id
    private String key;
    private String value;

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
