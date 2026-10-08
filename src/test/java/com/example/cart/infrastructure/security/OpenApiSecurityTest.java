package com.example.cart.infrastructure.security;

import com.example.cart.business.service.AuthService;
import com.example.cart.business.service.CartService;
import com.example.cart.business.service.ProductService;
import com.example.cart.presentation.controller.AuthController;
import com.example.cart.presentation.controller.CartController;
import com.example.cart.presentation.controller.HealthController;
import com.example.cart.presentation.controller.ProductController;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, ProductController.class, CartController.class, HealthController.class})
@Import({SecurityConfiguration.class, SecurityIntegrationTest.JwtTestConfiguration.class,
        SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class, SpringDocSecurityConfiguration.class})
class OpenApiSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private CartService cartService;

    @Test
    void documentsBearerJwtSchemeAndAllCartOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/cart'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/cart/items'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/cart/items/{productId}'].put.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/cart/items/{productId}'].delete.security[0].bearerAuth").isArray());
    }

    @Test
    void publicOperationsDoNotRequireBearerAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/health'].get.security").doesNotExist());
    }
}
