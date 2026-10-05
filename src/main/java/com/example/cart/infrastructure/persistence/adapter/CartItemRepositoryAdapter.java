package com.example.cart.infrastructure.persistence.adapter;

import com.example.cart.business.model.CartItem;
import com.example.cart.business.repository.CartItemRepository;
import com.example.cart.infrastructure.persistence.entity.CartItemEntity;
import com.example.cart.infrastructure.persistence.repository.JpaCartItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CartItemRepositoryAdapter implements CartItemRepository {

    private final JpaCartItemRepository jpaCartItemRepository;

    public CartItemRepositoryAdapter(
            JpaCartItemRepository jpaCartItemRepository
    ) {
        this.jpaCartItemRepository = jpaCartItemRepository;
    }

    @Override
    public Optional<CartItem> findByCartIdAndProductId(
            Long cartId,
            Long productId
    ) {
        return jpaCartItemRepository
                .findByCartIdAndProductId(cartId, productId)
                .map(this::toDomain);
    }

    @Override
    public List<CartItem> findByCartId(Long cartId) {
        return jpaCartItemRepository.findByCartId(cartId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public CartItem save(CartItem cartItem) {
        CartItemEntity entity = toEntity(cartItem);
        CartItemEntity savedEntity = jpaCartItemRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public void delete(CartItem cartItem) {
        jpaCartItemRepository.delete(toEntity(cartItem));
    }

    private CartItem toDomain(CartItemEntity entity) {
        return new CartItem(
                entity.getId(),
                entity.getCartId(),
                entity.getProductId(),
                entity.getQuantity()
        );
    }

    private CartItemEntity toEntity(CartItem cartItem) {
        return new CartItemEntity(
                cartItem.getId(),
                cartItem.getCartId(),
                cartItem.getProductId(),
                cartItem.getQuantity()
        );
    }
}