package com.example.cart.presentation.controller;

import com.example.cart.business.model.User;
import com.example.cart.business.service.AuthService;
import com.example.cart.presentation.dto.auth.LoginRequest;
import com.example.cart.presentation.dto.auth.LoginResponse;
import com.example.cart.presentation.dto.auth.RegisterRequest;
import com.example.cart.presentation.dto.auth.RegisterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        User user = authService.register(request.username(), request.password());
        RegisterResponse response = new RegisterResponse(user.getId(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
