package com.example.cart.infrastructure.persistence.repository;

import com.example.cart.infrastructure.persistence.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaCartItemRepository
        extends JpaRepository<CartItemEntity, Long> {

    Optional<CartItemEntity> findByCartIdAndProductId(
            Long cartId,
            Long productId
    );

    List<CartItemEntity> findByCartId(Long cartId);
}