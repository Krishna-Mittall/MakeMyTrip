package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.ReviewPhoto;
import com.makemytrip.makemytrip.services.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {
    @Autowired
    private ReviewService reviewService;

    /** Body of a new review. */
    public static class ReviewRequest {
        public String userId;
        public String targetType;
        public String targetId;
        public int rating;
        public String title;
        public String text;
        public List<String> photoIds;
    }

    public static class ReplyRequest {
        public String text;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam String targetType, @RequestParam String targetId,
                                    @RequestParam(defaultValue = "helpful") String sort,
                                    @RequestParam(required = false) Integer rating,
                                    @RequestParam(defaultValue = "false") boolean withPhotos,
                                    @RequestParam(defaultValue = "false") boolean verifiedOnly,
                                    @RequestParam(required = false) String userId) {
        return reviewService.list(targetType.toUpperCase(), targetId, sort, rating, withPhotos, verifiedOnly, userId);
    }

    @GetMapping("/flag-reasons")
    public List<String> flagReasons() {
        return ReviewService.FLAG_REASONS;
    }

    @PostMapping
    public ReviewService.ReviewView save(@RequestBody ReviewRequest request) {
        return reviewService.save(request.userId, request.targetType == null ? "" : request.targetType.toUpperCase(),
                request.targetId, request.rating, request.title, request.text, request.photoIds);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, @RequestParam String userId) {
        reviewService.delete(id, userId);
        return Map.of("deleted", true);
    }

    @PostMapping("/{id}/helpful")
    public Map<String, Object> helpful(@PathVariable String id, @RequestParam String userId) {
        return reviewService.toggleHelpful(id, userId);
    }

    @PostMapping("/{id}/reply")
    public ReviewService.ReviewView reply(@PathVariable String id, @RequestParam String userId, @RequestBody ReplyRequest request) {
        return reviewService.reply(id, userId, request.text);
    }

    @PostMapping("/{id}/flag")
    public Map<String, Object> flag(@PathVariable String id, @RequestParam String userId, @RequestParam String reason,
                                    @RequestParam(required = false) String details) {
        return reviewService.flag(id, userId, reason, details);
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam String userId, @RequestParam("file") MultipartFile file) {
        try {
            String id = reviewService.savePhoto(userId, file.getContentType(), file.getBytes());
            return Map.of("id", id, "url", "/reviews/photos/" + id);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The photo could not be read");
        }
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        ReviewPhoto p = reviewService.photo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(p.getData());
    }
}
