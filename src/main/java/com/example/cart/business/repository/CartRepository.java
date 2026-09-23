package com.example.cart.business.repository;

import com.example.cart.business.model.Cart;

public interface CartRepository {
    Cart findByUserId(Long userId);
    Cart save(Cart cart);
}
