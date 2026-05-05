package com.edulingo.security;

import com.edulingo.entity.User;
import com.edulingo.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Component
public class SecurityUtils {

    private final UserRepository users;

    public SecurityUtils(UserRepository users) {
        this.users = users;
    }

    public Mono<String> currentEmail() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getPrincipal)
                .cast(String.class)
                .flatMap(userId -> Mono.fromCallable(
                        () -> users.findById(UUID.fromString(userId))
                                .map(User::getEmail)
                                .orElseThrow()
                ).subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<UUID> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> UUID.fromString(ctx.getAuthentication().getPrincipal().toString()));
    }
}
