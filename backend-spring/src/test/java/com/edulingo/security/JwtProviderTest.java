package com.edulingo.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtProvider")
class JwtProviderTest {

    // 32-byte key encoded in Base64 (required for HS256)
    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString("edulingo-test-secret-key-32bytes".getBytes());
    private static final long ONE_HOUR_MS = 3_600_000L;

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(TEST_SECRET, ONE_HOUR_MS);
    }

    @Test
    @DisplayName("generateToken() phải trả về token không rỗng")
    void generateToken_returnsNonBlankToken() {
        String token = jwtProvider.generateToken(UUID.randomUUID(), "user@test.com", "STUDENT");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("parse() phải trả về đúng subject và claims")
    void parse_returnsCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "learner@test.com";
        String role  = "STUDENT";

        String token  = jwtProvider.generateToken(userId, email, role);
        Claims claims = jwtProvider.parse(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.get("role",  String.class)).isEqualTo(role);
    }

    @Test
    @DisplayName("isValid() phải trả true với token hợp lệ")
    void isValid_returnsTrueForValidToken() {
        String token = jwtProvider.generateToken(UUID.randomUUID(), "a@b.com", "STUDENT");
        assertThat(jwtProvider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid() phải trả false với chuỗi garbage")
    void isValid_returnsFalseForGarbage() {
        assertThat(jwtProvider.isValid("not.a.valid.jwt")).isFalse();
        assertThat(jwtProvider.isValid("")).isFalse();
    }

    @Test
    @DisplayName("isValid() phải trả false với token đã hết hạn")
    void isValid_returnsFalseForExpiredToken() throws InterruptedException {
        JwtProvider shortLived = new JwtProvider(TEST_SECRET, 1L); // expires in 1 ms
        String token = shortLived.generateToken(UUID.randomUUID(), "a@b.com", "STUDENT");
        Thread.sleep(20);
        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("Hai lần generateToken() với cùng input phải cho token khác nhau (iat khác)")
    void generateToken_differentCallsDifferentTokens() throws InterruptedException {
        UUID id = UUID.randomUUID();
        String t1 = jwtProvider.generateToken(id, "a@b.com", "STUDENT");
        Thread.sleep(1100); // iat chỉ có độ phân giải giây
        String t2 = jwtProvider.generateToken(id, "a@b.com", "STUDENT");
        assertThat(t1).isNotEqualTo(t2);
    }
}
