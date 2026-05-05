package com.edulingo.service;

import com.edulingo.dto.AuthResponse;
import com.edulingo.dto.LoginRequest;
import com.edulingo.dto.RegisterRequest;
import com.edulingo.entity.User;
import com.edulingo.mapper.UserMapper;
import com.edulingo.repository.UserRepository;
import com.edulingo.security.JwtProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final UserMapper userMapper;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final PersonalizationService personalization;

    public AuthService(UserRepository users,
                       UserMapper userMapper,
                       JwtProvider jwtProvider,
                       PasswordEncoder passwordEncoder,
                       PersonalizationService personalization) {
        this.users = users;
        this.userMapper = userMapper;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.personalization = personalization;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = users.save(userMapper.toEntity(req));
        var profile = personalization.getOrCreate(user.getEmail());
        profile.setUserId(user.getId());
        String token = jwtProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return userMapper.toResponse(user, token);
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = jwtProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return userMapper.toResponse(user, token);
    }
}
