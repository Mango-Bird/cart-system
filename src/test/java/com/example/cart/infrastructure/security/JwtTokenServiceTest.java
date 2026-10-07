package com.example.cart.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.cart.business.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final Long USER_ID = 42L;
    private static final String USERNAME = "bird";
    private static final Instant NOW =
            Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TOKEN_TTL =
            Duration.ofMinutes(15);

    private static final String TEST_SECRET = testSecret((byte) 1);

    private JwtTokenService jwtTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        jwtTokenService = new JwtTokenService(
                TEST_SECRET,
                TOKEN_TTL,
                clock
        );

        user = new User(
                USER_ID,
                USERNAME,
                "hashed-password"
        );
    }

    @Test
    void generateTokenReturnsNonEmptyToken() {
        String token = jwtTokenService.generateToken(user);

        assertFalse(token.isBlank());
    }

    @Test
    void generatedTokenContainsCorrectUserId() {
        String token = jwtTokenService.generateToken(user);

        Long result = jwtTokenService.parseUserId(token);

        assertEquals(USER_ID, result);
    }

    @Test
    void generatedTokenContainsUsernameClaim() {
        String token = jwtTokenService.generateToken(user);

        String result = jwtTokenService.parseUsername(token);

        assertEquals(USERNAME, result);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtTokenService.generateToken(user);
        String tamperedToken = tamperPayload(token);

        assertThrows(
                JwtException.class,
                () -> jwtTokenService.parseUserId(tamperedToken)
        );
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = jwtTokenService.generateToken(user);

        JwtTokenService anotherTokenService =
                new JwtTokenService(
                        testSecret((byte) 2),
                        TOKEN_TTL,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        assertThrows(
                JwtException.class,
                () -> anotherTokenService.parseUserId(token)
        );
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwtTokenService.generateToken(user);

        Clock afterExpiration = Clock.fixed(
                NOW.plus(TOKEN_TTL).plusSeconds(1),
                ZoneOffset.UTC
        );

        JwtTokenService verifierAfterExpiration =
                new JwtTokenService(
                        TEST_SECRET,
                        TOKEN_TTL,
                        afterExpiration
                );

        assertThrows(
                ExpiredJwtException.class,
                () -> verifierAfterExpiration.parseUserId(token)
        );
    }

    @Test
    void userWithoutIdCannotGenerateToken() {
        User userWithoutId = new User(
                null,
                USERNAME,
                "hashed-password"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtTokenService.generateToken(userWithoutId)
        );
    }

    @Test
    void tooShortSigningKeyIsRejected() {
        String shortSecret = Base64.getEncoder()
                .encodeToString(new byte[16]);

        assertThrows(
                WeakKeyException.class,
                () -> new JwtTokenService(
                        shortSecret,
                        TOKEN_TTL
                )
        );
    }

    private static String testSecret(byte value) {
        byte[] secretBytes = new byte[32];
        Arrays.fill(secretBytes, value);

        return Base64.getEncoder()
                .encodeToString(secretBytes);
    }

    private static String tamperPayload(String token) {
        String[] parts = token.split("\\.");

        String payload = parts[1];
        int lastIndex = payload.length() - 1;
        char lastCharacter = payload.charAt(lastIndex);
        char replacement = lastCharacter == 'A' ? 'B' : 'A';

        String tamperedPayload =
                payload.substring(0, lastIndex) + replacement;

        return parts[0]
                + "."
                + tamperedPayload
                + "."
                + parts[2];
    }
}