package com.example.cart.presentation.controller;

import  com.example.cart.business.model.Cart;
import com.example.cart.business.model.CartItem;
import com.example.cart.business.service.CartService;
import com.example.cart.presentation.dto.cart.AddCartItemRequest;
import com.example.cart.presentation.dto.cart.CartItemResponse;
import com.example.cart.presentation.dto.cart.CartResponse;
import com.example.cart.presentation.dto.cart.UpdateCartItemRequest;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get the current user's cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart returned", content = @Content(
                    schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cart not found", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> getCart(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                toResponse(cartService.getCartByUserId(userId))
        );
    }

    @PostMapping("/items")
    @Operation(summary = "Add an item to the current user's cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart item added", content = @Content(
                    schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid quantity or insufficient stock", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cart or product not found", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal Long userId,
            @RequestBody AddCartItemRequest request
    ) {
        Cart cart = cartService.addItem(
                userId,
                request.productId(),
                request.quantity()
        );

        return ResponseEntity.ok(toResponse(cart));
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Update an item quantity in the current user's cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart item updated", content = @Content(
                    schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid quantity or insufficient stock", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cart, product, or cart item not found", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long productId,
            @RequestBody UpdateCartItemRequest request
    ) {
        Cart cart = cartService.updateItem(
                userId,
                productId,
                request.quantity()
        );

        return ResponseEntity.ok(toResponse(cart));
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Delete an item from the current user's cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart item deleted", content = @Content(
                    schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cart or cart item not found", content = @Content(
                    schema = @Schema(implementation = com.example.cart.presentation.dto.common.ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> deleteItem(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long productId
    ) {
        Cart cart = cartService.deleteItem(userId, productId);
        return ResponseEntity.ok(toResponse(cart));
    }

    private static CartResponse toResponse(Cart cart) {
        List<CartItem> items = cart.getItems();
        List<CartItemResponse> itemResponses = items == null
                ? List.of()
                : items.stream()
                        .map(CartController::toResponse)
                        .toList();
        return new CartResponse(cart.getId(), cart.getUserId(), itemResponses);
    }

    private static CartItemResponse toResponse(CartItem cartItem) {
        return new CartItemResponse(cartItem.getProductId(), cartItem.getQuantity());
    }
}
