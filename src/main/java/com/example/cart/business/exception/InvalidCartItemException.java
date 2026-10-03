package com.example.cart.business.exception;

public class InvalidCartItemException extends RuntimeException {
    public InvalidCartItemException(String message) {
        super(message);
    }
}
