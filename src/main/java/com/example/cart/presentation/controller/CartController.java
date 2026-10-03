package com.example.cart.presentation.controller;

import com.example.cart.business.model.Cart;
import com.example.cart.business.model.CartItem;
import com.example.cart.business.service.CartService;
import com.example.cart.presentation.dto.cart.AddCartItemRequest;
import com.example.cart.presentation.dto.cart.CartItemResponse;
import com.example.cart.presentation.dto.cart.CartResponse;
import com.example.cart.presentation.dto.cart.UpdateCartItemRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ResponseEntity<CartResponse> getCart(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(toResponse(cartService.getCartByUserId(userId)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody AddCartItemRequest request) {
        Cart cart = cartService.addItem(userId, request.productId(), request.quantity());
        return ResponseEntity.ok(toResponse(cart));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long productId,
            @RequestBody UpdateCartItemRequest request) {
        Cart cart = cartService.updateItem(userId, productId, request.quantity());
        return ResponseEntity.ok(toResponse(cart));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> deleteItem(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long productId) {
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
