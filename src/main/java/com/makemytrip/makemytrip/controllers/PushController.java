package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.repositories.UserRepository;
import com.makemytrip.makemytrip.services.NotificationService;
import com.makemytrip.makemytrip.services.PushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Browser push notifications: the browser sends its subscription here. */
@RestController
@RequestMapping("/push")
@CrossOrigin(origins = "*")
public class PushController {
    @Autowired
    private PushService pushService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/key")
    public Map<String, Object> key() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", pushService.enabled());
        m.put("publicKey", pushService.publicKey());
        return m;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam String userId) {
        return Map.of("enabled", pushService.enabled(), "subscriptions", pushService.count(userId));
    }

    /** Body: what the browser's PushSubscription.toJSON() gives: { endpoint, keys: { p256dh, auth } }. */
    @SuppressWarnings("unchecked")
    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@RequestParam String userId, @RequestBody Map<String, Object> body) {
        requireUser(userId);
        if (!pushService.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Push notifications are not available on this server");
        }
        Object keys = body.get("keys");
        Map<String, Object> k = keys instanceof Map ? (Map<String, Object>) keys : Map.of();
        pushService.subscribe(userId, str(body.get("endpoint")), str(k.get("p256dh")), str(k.get("auth")));
        return Map.of("subscribed", true, "subscriptions", pushService.count(userId));
    }

    @DeleteMapping("/subscribe")
    public Map<String, Object> unsubscribe(@RequestParam String userId, @RequestParam String endpoint) {
        pushService.unsubscribe(userId, endpoint);
        return Map.of("subscribed", false, "subscriptions", pushService.count(userId));
    }

    /** Sends a test notification (in the app and as a push message) so the user can see that it works. */
    @PostMapping("/test")
    public Map<String, Object> test(@RequestParam String userId) {
        requireUser(userId);
        notificationService.notifyUser(userId, "SYSTEM", "Notifications are on",
                "You will get flight status, refund and price alerts like this one.", "/flight-status");
        return Map.of("sent", true, "subscriptions", pushService.count(userId));
    }

    private void requireUser(String userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
