package com.example.cart.presentation.dto.cart;

import java.math.BigDecimal;

public record CartItemResponse(Long itemId, Long productId, String productName, BigDecimal price,
                               Integer quantity, BigDecimal subtotal) {
}
