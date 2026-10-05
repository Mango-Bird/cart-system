package com.example.cart.infrastructure.persistence.adapter;

import com.example.cart.business.model.Cart;
import com.example.cart.business.model.CartItem;
import com.example.cart.business.repository.CartRepository;
import com.example.cart.infrastructure.persistence.entity.CartEntity;
import com.example.cart.infrastructure.persistence.entity.CartItemEntity;
import com.example.cart.infrastructure.persistence.repository.JpaCartItemRepository;
import com.example.cart.infrastructure.persistence.repository.JpaCartRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CartRepositoryAdapter implements CartRepository {

    private final JpaCartRepository jpaCartRepository;
    private final JpaCartItemRepository jpaCartItemRepository;

    public CartRepositoryAdapter(
            JpaCartRepository jpaCartRepository,
            JpaCartItemRepository jpaCartItemRepository
    ) {
        this.jpaCartRepository = jpaCartRepository;
        this.jpaCartItemRepository = jpaCartItemRepository;
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        return jpaCartRepository.findByUserId(userId)
                .map(this::toDomain);
    }

    @Override
    public Optional<Cart> findById(Long id) {
        return jpaCartRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Cart save(Cart cart) {
        CartEntity entity = toEntity(cart);
        CartEntity savedEntity = jpaCartRepository.save(entity);
        return toDomain(savedEntity);
    }

    private Cart toDomain(CartEntity entity) {
        List<CartItem> items = jpaCartItemRepository
                .findByCartId(entity.getId())
                .stream()
                .map(this::toCartItemDomain)
                .toList();

        return new Cart(
                entity.getId(),
                entity.getUserId(),
                items
        );
    }

    private CartEntity toEntity(Cart cart) {
        return new CartEntity(
                cart.getId(),
                cart.getUserId()
        );
    }

    private CartItem toCartItemDomain(CartItemEntity entity) {
        return new CartItem(
                entity.getId(),
                entity.getCartId(),
                entity.getProductId(),
                entity.getQuantity()
        );
    }
}