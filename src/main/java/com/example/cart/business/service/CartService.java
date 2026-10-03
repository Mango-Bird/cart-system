package com.example.cart.business.service;

import com.example.cart.business.cache.CartCache;
import com.example.cart.business.exception.CartNotFoundException;
import com.example.cart.business.exception.InsufficientStockException;
import com.example.cart.business.exception.InvalidCartItemException;
import com.example.cart.business.exception.ProductNotFoundException;
import com.example.cart.business.model.Cart;
import com.example.cart.business.model.CartItem;
import com.example.cart.business.model.Product;
import com.example.cart.business.repository.CartItemRepository;
import com.example.cart.business.repository.CartRepository;
import com.example.cart.business.repository.ProductRepository;
import java.util.List;

public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CartCache cartCache;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductRepository productRepository, CartCache cartCache) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
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

    public Cart addItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new InvalidCartItemException("Cart item quantity must be greater than zero");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (quantity > product.getStock()) {
            throw new InsufficientStockException(productId, quantity, product.getStock());
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(new Cart(null, userId, List.of())));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElse(null);

        long finalQuantity = quantity;
        if (cartItem != null) {
            finalQuantity += cartItem.getQuantity();
        }

        if (finalQuantity > product.getStock()) {
            throw new InsufficientStockException(productId, finalQuantity, product.getStock());
        }

        if (cartItem == null) {
            cartItem = new CartItem(null, cart.getId(), productId, (int) finalQuantity);
        } else {
            cartItem.setQuantity((int) finalQuantity);
        }

        cartItemRepository.save(cartItem);
        cartCache.evict(userId);
        return cart;
    }
}
