package com.example.cart.presentation.exception;

import com.example.cart.business.exception.AuthenticationException;
import com.example.cart.business.exception.CartItemNotFoundException;
import com.example.cart.business.exception.CartNotFoundException;
import com.example.cart.business.exception.InsufficientStockException;
import com.example.cart.business.exception.InvalidCartItemException;
import com.example.cart.business.exception.ProductNotFoundException;
import com.example.cart.business.exception.UserAlreadyExistsException;
import com.example.cart.presentation.dto.common.ErrorResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ProductNotFoundException.class,
            CartNotFoundException.class,
            CartItemNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException exception) {
        return errorResponse(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException exception) {
        return errorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({InvalidCartItemException.class, InsufficientStockException.class})
    public ResponseEntity<ErrorResponse> handleInvalidCartRequest(RuntimeException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message) {
        ErrorResponse response = new ErrorResponse(status.value(), message, Instant.now());
        return ResponseEntity.status(status).body(response);
    }
}
