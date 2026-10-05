package com.example.cart.infrastructure.persistence.repository;

import com.example.cart.infrastructure.persistence.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaCartRepository
        extends JpaRepository<CartEntity, Long> {

    Optional<CartEntity> findByUserId(Long userId);
}