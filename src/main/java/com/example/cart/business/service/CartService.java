package com.example.cart.business.service;

import com.example.cart.business.cache.CartCache;
import com.example.cart.business.exception.CartItemNotFoundException;
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
import java.util.Objects;

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
                .orElseGet(() -> cartRepository.save(
                                new Cart(null, userId, List.of())));
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

        CartItem savedCartItem = cartItemRepository.save(cartItem);
        cart.getItems().removeIf(item -> Objects.equals(item.getProductId(), productId));
        cart.getItems().add(savedCartItem);
        cartCache.evict(userId);
        return cart;
    }

    public Cart updateItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new InvalidCartItemException("Cart item quantity must be greater than zero");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new CartItemNotFoundException(cart.getId(), productId));

        if (quantity > product.getStock()) {
            throw new InsufficientStockException(productId, quantity, product.getStock());
        }

        cartItem.setQuantity(quantity);
        CartItem savedCartItem = cartItemRepository.save(cartItem);
        cart.getItems().removeIf(item -> Objects.equals(item.getProductId(), productId));
        cart.getItems().add(savedCartItem);
        cartCache.evict(userId);
        return cart;
    }

    public Cart deleteItem(Long userId, Long productId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new CartItemNotFoundException(cart.getId(), productId));

        cartItemRepository.delete(cartItem);
        cart.getItems().removeIf(item -> Objects.equals(item.getProductId(), productId));
        cartCache.evict(userId);
        return cart;
    }
}
