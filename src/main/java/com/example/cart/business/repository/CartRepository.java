package com.example.cart.business.repository;

import com.example.cart.business.model.Cart;
import java.util.Optional;

public interface CartRepository {
    Optional<Cart> findByUserId(Long userId);

    Optional<Cart> findById(Long id);

    Cart save(Cart cart);
}
