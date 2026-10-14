package com.makemytrip.makemytrip.controllers;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.repositories.UserRepository;
import com.makemytrip.makemytrip.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Saved seat / room preferences of a user. */
@RestController
@RequestMapping("/preferences")
@CrossOrigin(origins = "*")
public class PreferenceController {
    @Autowired
    private UserRepository userRepository;

    private Users user(String id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @GetMapping("/{userId}")
    public Users.Preferences get(@PathVariable String userId) {
        Users.Preferences p = user(userId).getPreferences();
        return p == null ? new Users.Preferences() : p;
    }

    @PutMapping("/{userId}")
    public Users.Preferences save(@PathVariable String userId, @RequestBody Users.Preferences updated) {
        Users user = user(userId);
        Users.Preferences p = user.getPreferences() == null ? new Users.Preferences() : user.getPreferences();
        if (updated.getSeatPosition() != null) {
            p.setSeatPosition(updated.getSeatPosition());
        }
        if (updated.getSeatTier() != null) {
            p.setSeatTier(updated.getSeatTier());
        }
        if (updated.getRoomType() != null) {
            p.setRoomType(updated.getRoomType());
        }
        p.setUpdatedAt(TimeUtil.now().toInstant().toString());
        user.setPreferences(p);
        userRepository.save(user);
        return p;
    }
}
