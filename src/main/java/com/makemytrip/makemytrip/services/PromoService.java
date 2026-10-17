package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Promo codes: a flat discount on a booking, with a minimum booking amount. Checked again when the booking is made. */
@Service
public class PromoService {
    public static class Promo {
        public final String code;
        public final String type;        // FLIGHT
        public final double discount;
        public final double minAmount;
        public final String description;
        public final String terms;

        Promo(String code, String type, double discount, double minAmount, String description, String terms) {
            this.code = code;
            this.type = type;
            this.discount = discount;
            this.minAmount = minAmount;
            this.description = description;
            this.terms = terms;
        }
    }

    private static final List<Promo> PROMOS = List.of(
            new Promo("MMTSECURE", "FLIGHT", 299, 3000,
                    "Get an instant discount of ₹299 on your flight booking.",
                    "Valid on flight bookings with a total of ₹3,000 or more. One promo code per booking."),
            new Promo("SPECIALUPI", "FLIGHT", 362, 5000,
                    "Get a bigger instant discount of ₹362 on flight bookings of ₹5,000 or more.",
                    "Valid on flight bookings with a total of ₹5,000 or more. One promo code per booking."));

    public List<Map<String, Object>> offers(String type) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Promo p : PROMOS) {
            if (type == null || type.isBlank() || p.type.equalsIgnoreCase(type)) {
                out.add(view(p));
            }
        }
        return out;
    }

    /** The promo with this code, or an error when the code does not exist / is for another kind of booking. */
    public Promo find(String code, String type) {
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a promo code");
        }
        String c = code.trim().toUpperCase();
        for (Promo p : PROMOS) {
            if (p.code.equals(c)) {
                if (!p.type.equalsIgnoreCase(type)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, c + " is not valid for this booking");
                }
                return p;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "\"" + c + "\" is not a valid promo code");
    }

    /**
     * @param amountBeforePromo the booking total before the promo code is taken off
     */
    public Promo validate(String code, String type, double amountBeforePromo) {
        Promo p = find(code, type);
        if (amountBeforePromo < p.minAmount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, p.code + " needs a booking total of at least ₹" + Math.round(p.minAmount));
        }
        return p;
    }

    public Map<String, Object> view(Promo p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.code);
        m.put("type", p.type);
        m.put("discount", p.discount);
        m.put("minAmount", p.minAmount);
        m.put("description", p.description);
        m.put("terms", p.terms);
        return m;
    }
}
