package com.example.cart.business.repository;

import com.example.cart.business.model.CartItem;

public interface CartItemRepository {
    CartItem findById(Long id);
    CartItem findByIdAndCartId(Long itemId, Long cartId);
    CartItem findByCartIdAndProductId(Long cartId, Long productId);
    CartItem save(CartItem cartItem);
    void deleteById(Long id);
}
