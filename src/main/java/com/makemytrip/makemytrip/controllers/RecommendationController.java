package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.services.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Personalised recommendations, the "helpful / not relevant" feedback and the behaviour log that feeds them. */
@RestController
@RequestMapping("/recommendations")
@CrossOrigin(origins = "*")
public class RecommendationController {
    @Autowired
    private RecommendationService recommendationService;

    /** Without a userId the answer is the list of trending items. */
    @GetMapping
    public Map<String, Object> recommendations(@RequestParam(required = false) String userId,
                                               @RequestParam(defaultValue = "6") int limit,
                                               @RequestParam(required = false) String type) {
        return recommendationService.recommend(userId, limit, type);
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(@RequestParam String userId, @RequestParam String itemType,
                                        @RequestParam String itemId, @RequestParam String value) {
        return recommendationService.feedback(userId, itemType, itemId, value);
    }

    /** type: VIEW (itemType + itemId) or SEARCH (query = destination). */
    @PostMapping("/interaction")
    public Map<String, Object> interaction(@RequestParam String userId, @RequestParam(defaultValue = "VIEW") String type,
                                           @RequestParam(required = false) String itemType, @RequestParam(required = false) String itemId,
                                           @RequestParam(required = false) String query) {
        recommendationService.log(userId, itemType, itemId, type, query);
        return Map.of("logged", true);
    }
}
