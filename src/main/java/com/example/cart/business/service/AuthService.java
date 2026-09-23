package com.example.cart.business.service;

import com.example.cart.business.model.User;

public interface AuthService {
    User register(String name, String email, String password);
    String login(String email, String password);
}
