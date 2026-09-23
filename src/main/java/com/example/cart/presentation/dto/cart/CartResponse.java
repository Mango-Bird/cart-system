package com.example.cart.presentation.dto.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Long cartId, List<CartItemResponse> items, BigDecimal total) {
}
