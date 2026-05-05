package com.edulingo.controller;

import com.edulingo.dto.AuthResponse;
import com.edulingo.dto.LoginRequest;
import com.edulingo.dto.RegisterRequest;
import com.edulingo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Mono.fromCallable(() -> authService.register(req))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return Mono.fromCallable(() -> authService.login(req))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
