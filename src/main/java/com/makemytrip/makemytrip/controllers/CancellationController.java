package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.models.Refund;
import com.makemytrip.makemytrip.services.CancellationService;
import com.makemytrip.makemytrip.services.RefundPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cancellation")
@CrossOrigin(origins = "*")
public class CancellationController {
    @Autowired
    private CancellationService cancellationService;

    private List<String> split(String value) {
        List<String> list = new ArrayList<>();
        if (value != null) {
            for (String s : value.split(",")) {
                if (!s.isBlank()) {
                    list.add(s.trim());
                }
            }
        }
        return list;
    }

    @GetMapping("/reasons")
    public List<String> reasons() {
        return CancellationService.REASONS;
    }

    @GetMapping("/policy")
    public Map<String, Object> policy() {
        return Map.of("flight", RefundPolicy.table(true), "hotel", RefundPolicy.table(false));
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam String userId, @RequestParam String reference,
                                       @RequestParam(required = false) String seats,
                                       @RequestParam(required = false) Integer quantity) {
        return cancellationService.preview(userId, reference, split(seats), quantity);
    }

    @PostMapping("/cancel")
    public Refund cancel(@RequestParam String userId, @RequestParam String reference, @RequestParam String reason,
                         @RequestParam(required = false) String seats, @RequestParam(required = false) Integer quantity,
                         @RequestParam(required = false) String comments) {
        return cancellationService.cancel(userId, reference, split(seats), quantity, reason, comments);
    }

    @GetMapping("/refunds")
    public List<Refund> refunds(@RequestParam String userId) {
        return cancellationService.refundsOf(userId);
    }

    @GetMapping("/refunds/{id}")
    public Refund refund(@PathVariable String id, @RequestParam String userId) {
        return cancellationService.get(id, userId);
    }
}
