package com.example.cart.business.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long cartId, Long productId) {
        super("Cart item not found for cart: " + cartId + ", product: " + productId);
    }
}
