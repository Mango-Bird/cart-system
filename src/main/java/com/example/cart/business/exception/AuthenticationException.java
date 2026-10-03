package com.example.cart.business.exception;

public class AuthenticationException extends RuntimeException {
    public AuthenticationException() {
        super("Invalid username or password");
    }
}
