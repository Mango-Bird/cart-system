package com.example.cart.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.example.cart.business.model.User;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final Long USER_ID = 42L;
    private static final String USERNAME = "bird";

    private static final Instant NOW =
            Instant.parse("2026-01-01T00:00:00Z");

    private static final String TEST_SECRET =
            testSecret((byte) 1);

    @Mock
    private FilterChain filterChain;

    private JwtTokenService jwtTokenService;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtTokenService = new JwtTokenService(
                TEST_SECRET,
                Duration.ofMinutes(15),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        filter = new JwtAuthenticationFilter(
                jwtTokenService
        );

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthorizationHeaderDoesNotAuthenticateRequest()
            throws Exception {
        filter.doFilter(request, response, filterChain);

        assertNull(currentAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonBearerAuthorizationHeaderDoesNotAuthenticateRequest()
            throws Exception {
        request.addHeader(
                "Authorization",
                "Basic credentials"
        );

        filter.doFilter(request, response, filterChain);

        assertNull(currentAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validBearerTokenAuthenticatesUserId()
            throws Exception {
        User user = new User(
                USER_ID,
                USERNAME,
                "hashed-password"
        );

        String token = jwtTokenService.generateToken(user);

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        filter.doFilter(request, response, filterChain);

        Authentication authentication =
                currentAuthentication();

        assertTrue(authentication.isAuthenticated());
        assertInstanceOf(
                Long.class,
                authentication.getPrincipal()
        );
        assertEquals(
                USER_ID,
                authentication.getPrincipal()
        );
        assertTrue(authentication.getAuthorities().isEmpty());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidBearerTokenDoesNotAuthenticateRequest()
            throws Exception {
        request.addHeader(
                "Authorization",
                "Bearer invalid-token"
        );

        filter.doFilter(request, response, filterChain);

        assertNull(currentAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication();
    }

    private static String testSecret(byte value) {
        byte[] secretBytes = new byte[32];
        Arrays.fill(secretBytes, value);

        return Base64.getEncoder()
                .encodeToString(secretBytes);
    }
}