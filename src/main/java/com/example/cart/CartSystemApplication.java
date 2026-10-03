package com.example.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
		title = "Cart System API",
		description = "REST API for Cart System - Phase 1",
		version = "1.0.0"))
public class CartSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(CartSystemApplication.class, args);
	}

}
