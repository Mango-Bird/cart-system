package com.example.cart.business.repository;

import com.example.cart.business.model.CartItem;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    List<CartItem> findByCartId(Long cartId);

    CartItem save(CartItem cartItem);

    void delete(CartItem cartItem);
}
