package com.makemytrip.makemytrip.services;

import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.Users;
import com.makemytrip.makemytrip.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Checks the role of the user who calls an admin / moderator endpoint. */
@Service
public class AccessService {
    @Autowired
    private UserRepository userRepository;

    public Users requireAdmin(String userId) {
        Users user = find(userId);
        if (!"ADMIN".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an admin can do this");
        }
        return user;
    }

    /** Admins and moderators may review flagged content. */
    public Users requireModerator(String userId) {
        Users user = find(userId);
        if (!"ADMIN".equals(user.getRole()) && !"MODERATOR".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only a moderator or admin can do this");
        }
        return user;
    }

    /** Any logged-in user. */
    public Users requireUser(String userId) {
        return find(userId);
    }

    private Users find(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in");
        }
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in"));
    }
}
