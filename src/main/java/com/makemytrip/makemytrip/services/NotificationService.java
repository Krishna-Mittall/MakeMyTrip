package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.models.Notification;
import com.makemytrip.makemytrip.repositories.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** In-app notifications. They are stored, sent live to the open browser tab and (optionally) as a push message. */
@Service
public class NotificationService {
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EventHub eventHub;
    @Autowired
    private PushService pushService;

    public Notification notifyUser(String userId, String type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        n.setCreatedAt(Instant.now());
        n.setRead(false);
        n = notificationRepository.save(n);
        eventHub.publishToUser(userId, "NOTIFICATION", n);
        pushService.sendToUser(userId, title, message, link, n.getId());       // also as a browser push message
        return n;
    }

    public List<Notification> list(String userId, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    public long unread(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(String id, String userId) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (userId.equals(n.getUserId()) && !n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(userId);
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }
}
