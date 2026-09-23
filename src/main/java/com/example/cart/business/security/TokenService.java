package com.example.cart.business.security;

public interface TokenService {
    String generateToken(Long userId, String email);
}
