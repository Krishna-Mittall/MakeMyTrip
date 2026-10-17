package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.services.PromoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/promo")
@CrossOrigin(origins = "*")
public class PromoController {
    @Autowired
    private PromoService promoService;

    @GetMapping("/offers")
    public List<Map<String, Object>> offers(@RequestParam(defaultValue = "FLIGHT") String type) {
        return promoService.offers(type);
    }

    /** amount = the booking total before the promo code. */
    @GetMapping("/validate")
    public Map<String, Object> validate(@RequestParam String code, @RequestParam(defaultValue = "FLIGHT") String type, @RequestParam double amount) {
        return promoService.view(promoService.validate(code, type, amount));
    }
}
