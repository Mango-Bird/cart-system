package com.example.cart.business.service;

import com.example.cart.business.model.Cart;
import com.example.cart.business.model.CartItem;

public interface CartService {
    Cart getCart(Long userId);
    CartItem addItem(Long userId, Long productId, Integer quantity);
    CartItem updateItem(Long userId, Long itemId, Integer quantity);
    void removeItem(Long userId, Long itemId);
}
