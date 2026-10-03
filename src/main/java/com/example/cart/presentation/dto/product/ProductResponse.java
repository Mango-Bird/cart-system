package com.example.cart.presentation.dto.product;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, BigDecimal price, Integer stock) {
}
