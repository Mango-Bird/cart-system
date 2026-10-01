package com.example.cart.business.security;

import com.example.cart.business.model.User;

public interface TokenService {
    String generateToken(User user);
}
