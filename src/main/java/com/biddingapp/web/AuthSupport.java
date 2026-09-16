package com.biddingapp.web;

import com.biddingapp.domain.User;
import com.biddingapp.security.AppUserDetails;
import com.biddingapp.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthSupport {

    private AuthSupport() {
    }

    public static User currentUser(Authentication authentication, UserService userService) {
        String username = currentUsername(authentication);
        if (username == null) {
            return null;
        }
        return userService.requireByUsername(username);
    }

    public static String currentUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUserDetails details) {
            return details.getUsername();
        }
        if (principal instanceof UserDetails details) {
            return details.getUsername();
        }
        if (principal instanceof String name && !"anonymousUser".equals(name)) {
            return name;
        }
        return null;
    }
}
