package com.biddingapp.service;

import com.biddingapp.domain.Role;
import com.biddingapp.domain.User;
import com.biddingapp.repository.UserRepository;
import com.biddingapp.web.dto.RegisterForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterForm form) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("That username is already taken");
        }
        if (userRepository.existsByEmail(form.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("That email is already registered");
        }

        User user = new User();
        user.setUsername(form.getUsername().trim());
        user.setEmail(form.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRole(Role.USER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User requireByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
