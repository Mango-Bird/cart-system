package com.example.cart.business.security;

public interface PasswordHasher {
    String hash(String password);

    boolean matches(String password, String hashedPassword);
}
