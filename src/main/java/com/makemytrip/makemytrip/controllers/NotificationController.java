package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.models.Notification;
import com.makemytrip.makemytrip.services.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {
    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public Map<String, Object> list(@RequestParam String userId, @RequestParam(defaultValue = "30") int limit) {
        List<Notification> items = notificationService.list(userId, limit);
        return Map.of("unread", notificationService.unread(userId), "items", items);
    }

    @PostMapping("/read-all")
    public Map<String, Object> readAll(@RequestParam String userId) {
        notificationService.markAllRead(userId);
        return Map.of("unread", 0);
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> read(@PathVariable String id, @RequestParam String userId) {
        notificationService.markRead(id, userId);
        return Map.of("unread", notificationService.unread(userId));
    }
}
