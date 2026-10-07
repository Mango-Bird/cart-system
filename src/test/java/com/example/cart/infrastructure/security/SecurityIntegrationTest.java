package com.example.cart.infrastructure.security;

import com.example.cart.business.model.Cart;
import com.example.cart.business.model.User;
import com.example.cart.business.service.CartService;
import com.example.cart.presentation.controller.CartController;
import com.example.cart.presentation.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        HealthController.class,
        CartController.class
})
@AutoConfigureMockMvc
@Import({
        SecurityConfiguration.class,
        SecurityIntegrationTest.JwtTestConfiguration.class
})
class SecurityIntegrationTest {

    private static final Long USER_ID = 123L;

    /*
     * Base64 của đúng 32 byte:
     * "0123456789abcdef0123456789abcdef"
     *
     * Đây chỉ là secret giả dành cho test.
     */
    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CartService cartService;

    @Test
    void healthEndpointWithoutJwtIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void cartEndpointWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cartEndpointWithInvalidJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header(
                                AUTHORIZATION,
                                "Bearer not-a-valid-jwt"
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtPropagatesLongUserIdToCartController() throws Exception {
        Cart cart = new Cart(
                10L,
                USER_ID,
                List.of()
        );

        when(cartService.getCartByUserId(USER_ID))
                .thenReturn(cart);

        String token = generateToken(USER_ID);

        mockMvc.perform(get("/api/cart")
                        .header(
                                AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.items").isArray());

        verify(cartService).getCartByUserId(USER_ID);
    }

    @Test
    void authenticatedRequestToDeniedEndpointReturnsForbidden()
            throws Exception {
        String token = generateToken(USER_ID);

        mockMvc.perform(get("/api/not-allowed")
                        .header(
                                AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void validJwtRequestDoesNotCreateHttpSession() throws Exception {
        Cart cart = new Cart(
                10L,
                USER_ID,
                List.of()
        );

        when(cartService.getCartByUserId(USER_ID))
                .thenReturn(cart);

        String token = generateToken(USER_ID);

        MvcResult result = mockMvc.perform(get("/api/cart")
                        .header(
                                AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isOk())
                .andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    private String generateToken(Long userId) {
        User user = new User(
                userId,
                "security-test-user",
                "unused-password-hash"
        );

        return jwtTokenService.generateToken(user);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        JwtTokenService jwtTokenService() {
            return new JwtTokenService(
                    TEST_SECRET,
                    Duration.ofMinutes(15)
            );
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtTokenService jwtTokenService
        ) {
            return new JwtAuthenticationFilter(jwtTokenService);
        }
    }
}