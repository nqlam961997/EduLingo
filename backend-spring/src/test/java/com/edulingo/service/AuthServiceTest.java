package com.edulingo.service;

import com.edulingo.dto.AuthResponse;
import com.edulingo.dto.LoginRequest;
import com.edulingo.dto.RegisterRequest;
import com.edulingo.entity.LearnerProfile;
import com.edulingo.entity.Role;
import com.edulingo.entity.User;
import com.edulingo.mapper.UserMapper;
import com.edulingo.repository.UserRepository;
import com.edulingo.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock UserRepository         users;
    @Mock UserMapper             userMapper;
    @Mock JwtProvider            jwtProvider;
    @Mock PasswordEncoder        passwordEncoder;
    @Mock PersonalizationService personalization;

    @InjectMocks AuthService service;

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register() thành công phải trả về AuthResponse có token")
    void register_success() {
        RegisterRequest req = new RegisterRequest("newuser@test.com", "Nguyen Van A", "password123");

        when(users.existsByEmail(req.email())).thenReturn(false);

        User savedUser = makeUser(req.email(), "Nguyen Van A");
        when(userMapper.toEntity(req)).thenReturn(savedUser);
        when(users.save(savedUser)).thenReturn(savedUser);

        LearnerProfile profile = new LearnerProfile();
        when(personalization.getOrCreate(savedUser.getEmail())).thenReturn(profile);

        when(jwtProvider.generateToken(any(UUID.class), anyString(), anyString()))
                .thenReturn("signed-jwt-token");

        AuthResponse expected = new AuthResponse("signed-jwt-token", req.email(), "Nguyen Van A", "STUDENT");
        when(userMapper.toResponse(savedUser, "signed-jwt-token")).thenReturn(expected);

        AuthResponse result = service.register(req);

        assertThat(result.token()).isEqualTo("signed-jwt-token");
        assertThat(result.email()).isEqualTo(req.email());
    }

    @Test
    @DisplayName("register() phải ném CONFLICT khi email đã tồn tại")
    void register_duplicateEmail_throwsConflict() {
        RegisterRequest req = new RegisterRequest("taken@test.com", "Existing User", "pass123");
        when(users.existsByEmail(req.email())).thenReturn(true);

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login() thành công phải trả về token")
    void login_success() {
        String email    = "user@test.com";
        String rawPass  = "correctPassword";
        String hashPass = "$2a$hashedPassword";

        LoginRequest req = new LoginRequest(email, rawPass);
        User user = makeUser(email, "Test User");
        user.setPasswordHash(hashPass);

        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPass, hashPass)).thenReturn(true);
        when(jwtProvider.generateToken(any(UUID.class), anyString(), anyString()))
                .thenReturn("login-token");

        AuthResponse expected = new AuthResponse("login-token", email, "Test User", "STUDENT");
        when(userMapper.toResponse(user, "login-token")).thenReturn(expected);

        AuthResponse result = service.login(req);

        assertThat(result.token()).isEqualTo("login-token");
    }

    @Test
    @DisplayName("login() phải ném UNAUTHORIZED khi sai mật khẩu")
    void login_wrongPassword_throwsUnauthorized() {
        String email = "user@test.com";
        LoginRequest req = new LoginRequest(email, "wrongPass");

        User user = makeUser(email, "Test User");
        user.setPasswordHash("$2a$correctHash");

        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "$2a$correctHash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("login() phải ném UNAUTHORIZED khi email không tồn tại")
    void login_unknownEmail_throwsUnauthorized() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@test.com", "pass")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User makeUser(String email, String fullName) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName(fullName);
        u.setPasswordHash("hashed");
        u.setRole(Role.STUDENT);
        return u;
    }
}
