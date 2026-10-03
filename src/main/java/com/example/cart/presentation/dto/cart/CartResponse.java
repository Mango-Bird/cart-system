package com.example.cart.presentation.dto.cart;

import java.util.List;

public record CartResponse(Long id, Long userId, List<CartItemResponse> items) {
}
