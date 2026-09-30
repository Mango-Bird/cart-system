package com.example.cart.business.cache;

import com.example.cart.business.model.Cart;

public interface CartCache {
    Cart get(Long userId);

    void put(Long userId, Cart cart);

    void evict(Long userId);
}
