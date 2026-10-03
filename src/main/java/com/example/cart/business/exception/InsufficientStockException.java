package com.example.cart.business.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long productId, long requestedQuantity, Integer availableStock) {
        super("Insufficient stock for product: " + productId
                + " (requested: " + requestedQuantity + ", available: " + availableStock + ")");
    }
}
