package com.example.cart.infrastructure.security;

import com.example.cart.business.model.User;
import com.example.cart.business.security.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class JwtTokenService implements TokenService {

    private static final String ISSUER = "cart-system";
    private static final String USERNAME_CLAIM = "username";

    private final SecretKey signingKey;
    private final Duration tokenTtl;
    private final Clock clock;
    private final JwtParser jwtParser;

    public JwtTokenService(String base64Secret, Duration tokenTtl) {
        this(base64Secret, tokenTtl, Clock.systemUTC());
    }

    JwtTokenService(
            String base64Secret,
            Duration tokenTtl,
            Clock clock
    ) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT secret must not be blank"
            );
        }

        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "JWT TTL must be greater than zero"
            );
        }

        if (clock == null) {
            throw new IllegalArgumentException(
                    "Clock must not be null"
            );
        }

        byte[] secretBytes = Decoders.BASE64.decode(base64Secret);

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.tokenTtl = tokenTtl;
        this.clock = clock;
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    @Override
    public String generateToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "User must not be null"
            );
        }

        if (user.getId() == null) {
            throw new IllegalArgumentException(
                    "User ID must not be null"
            );
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(tokenTtl);

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .claim(USERNAME_CLAIM, user.getUsername())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public String parseUsername(String token) {
        Claims claims = parseClaims(token);
        return claims.get(USERNAME_CLAIM, String.class);
    }

    private Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT must not be blank"
            );
        }

        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }
}