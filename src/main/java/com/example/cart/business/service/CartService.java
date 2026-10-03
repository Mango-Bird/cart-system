package com.example.cart.business.service;

import com.example.cart.business.cache.CartCache;
import com.example.cart.business.exception.CartNotFoundException;
import com.example.cart.business.model.Cart;
import com.example.cart.business.repository.CartRepository;

public class CartService {
    private final CartRepository cartRepository;
    private final CartCache cartCache;

    public CartService(CartRepository cartRepository, CartCache cartCache) {
        this.cartRepository = cartRepository;
        this.cartCache = cartCache;
    }

    public Cart getCartByUserId(Long userId) {
        Cart cachedCart = cartCache.get(userId);
        if (cachedCart != null) {
            return cachedCart;
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));
        cartCache.put(userId, cart);
        return cart;
    }
}
