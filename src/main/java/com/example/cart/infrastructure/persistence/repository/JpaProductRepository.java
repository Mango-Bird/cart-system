package com.example.cart.infrastructure.persistence.repository;

import com.example.cart.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductRepository
        extends JpaRepository<ProductEntity, Long> {
}