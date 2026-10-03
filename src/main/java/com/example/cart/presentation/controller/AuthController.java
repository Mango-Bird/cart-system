package com.example.cart.presentation.controller;

import com.example.cart.business.model.User;
import com.example.cart.business.service.AuthService;
import com.example.cart.presentation.dto.auth.LoginRequest;
import com.example.cart.presentation.dto.auth.LoginResponse;
import com.example.cart.presentation.dto.auth.RegisterRequest;
import com.example.cart.presentation.dto.auth.RegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(summary = "Register a user", description = "Creates a user account and returns its public profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered", content = @Content(
                    schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Username already exists", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class)))
    })
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        User user = authService.register(request.username(), request.password());
        RegisterResponse response = new RegisterResponse(user.getId(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates a user and returns an access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(
                    schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
