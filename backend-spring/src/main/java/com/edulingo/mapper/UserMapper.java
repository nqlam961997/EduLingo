package com.edulingo.mapper;

import com.edulingo.dto.AuthResponse;
import com.edulingo.dto.RegisterRequest;
import com.edulingo.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final PasswordEncoder passwordEncoder;

    public UserMapper(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public User toEntity(RegisterRequest req) {
        User user = new User();
        user.setEmail(req.email());
        user.setFullName(req.fullName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        return user;
    }

    public AuthResponse toResponse(User user, String token) {
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
