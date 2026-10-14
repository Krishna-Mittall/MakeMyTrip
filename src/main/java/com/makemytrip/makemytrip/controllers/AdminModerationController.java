package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.services.AccessService;
import com.makemytrip.makemytrip.services.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Moderation of flagged reviews (admins and moderators). */
@RestController
@RequestMapping("/admin/moderation")
@CrossOrigin(origins = "*")
public class AdminModerationController {
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private AccessService accessService;

    @GetMapping("/queue")
    public List<Map<String, Object>> queue(@RequestParam String userId, @RequestParam(defaultValue = "PENDING") String status) {
        accessService.requireModerator(userId);
        return reviewService.moderationQueue(status);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam String userId) {
        accessService.requireModerator(userId);
        return reviewService.moderationStats();
    }

    @PostMapping("/{id}/dismiss")
    public Map<String, Object> dismiss(@PathVariable String id, @RequestParam String userId, @RequestParam(required = false) String note) {
        Users moderator = accessService.requireModerator(userId);
        return reviewService.dismiss(id, moderator, note);
    }

    @PostMapping("/{id}/remove")
    public Map<String, Object> remove(@PathVariable String id, @RequestParam String userId, @RequestParam(required = false) String note) {
        Users moderator = accessService.requireModerator(userId);
        return reviewService.remove(id, moderator, note);
    }
}
