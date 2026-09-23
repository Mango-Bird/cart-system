package com.example.cart.presentation.dto.common;

import java.time.Instant;

public record ErrorResponse(int status, String message, Instant timestamp) {
}
