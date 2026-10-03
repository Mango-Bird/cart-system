package com.example.cart.presentation.controller;

import com.example.cart.business.model.Product;
import com.example.cart.business.service.ProductService;
import com.example.cart.presentation.dto.product.ProductResponse;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List products")
    @ApiResponse(responseCode = "200", description = "Products returned", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class))))
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> response = productService.getAllProducts().stream()
                .map(ProductController::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product", description = "Returns one product by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product returned", content = @Content(
                    schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product not found", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(toResponse(product));
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStock());
    }
}
