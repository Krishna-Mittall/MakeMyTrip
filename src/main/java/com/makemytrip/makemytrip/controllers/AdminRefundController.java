package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.models.Refund;
import com.makemytrip.makemytrip.services.AccessService;
import com.makemytrip.makemytrip.services.CancellationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Refund management for admins. */
@RestController
@RequestMapping("/admin/refunds")
@CrossOrigin(origins = "*")
public class AdminRefundController {
    @Autowired
    private CancellationService cancellationService;
    @Autowired
    private AccessService accessService;

    @GetMapping
    public List<Refund> list(@RequestParam String adminId, @RequestParam(required = false) String status) {
        accessService.requireAdmin(adminId);
        return cancellationService.all(status);
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics(@RequestParam String adminId) {
        accessService.requireAdmin(adminId);
        return cancellationService.analytics();
    }

    @PostMapping("/{id}/advance")
    public Refund advance(@PathVariable String id, @RequestParam String adminId) {
        accessService.requireAdmin(adminId);
        return cancellationService.adminAdvance(id);
    }
}
