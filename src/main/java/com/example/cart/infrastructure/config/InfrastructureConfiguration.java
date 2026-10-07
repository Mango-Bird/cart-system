package com.example.cart.infrastructure.config;

import com.example.cart.business.cache.CartCache;
import com.example.cart.business.repository.CartItemRepository;
import com.example.cart.business.repository.CartRepository;
import com.example.cart.business.repository.ProductRepository;
import com.example.cart.business.repository.UserRepository;
import com.example.cart.business.security.PasswordHasher;
import com.example.cart.business.security.TokenService;
import com.example.cart.business.service.AuthService;
import com.example.cart.business.service.CartService;
import com.example.cart.business.service.ProductService;
import com.example.cart.infrastructure.cache.RedisCartCache;
import com.example.cart.infrastructure.security.BCryptPasswordHasher;
import com.example.cart.infrastructure.security.JwtAuthenticationFilter;
import com.example.cart.infrastructure.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class InfrastructureConfiguration {

    @Bean
    public PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.ttl}") Duration tokenTtl
    ) {
        return new JwtTokenService(secret, tokenTtl);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenService jwtTokenService
    ) {
        return new JwtAuthenticationFilter(jwtTokenService);
    }

    @Bean
    public CartCache cartCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        return new RedisCartCache(redisTemplate, objectMapper);
    }

    @Bean
    public AuthService authService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            TokenService tokenService
    ) {
        return new AuthService(
                userRepository,
                passwordHasher,
                tokenService
        );
    }

    @Bean
    public ProductService productService(
            ProductRepository productRepository
    ) {
        return new ProductService(productRepository);
    }

    @Bean
    public CartService cartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            CartCache cartCache
    ) {
        return new CartService(
                cartRepository,
                cartItemRepository,
                productRepository,
                cartCache
        );
    }
}