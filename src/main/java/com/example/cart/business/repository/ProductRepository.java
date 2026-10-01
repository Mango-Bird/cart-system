package com.example.cart.business.repository;

import com.example.cart.business.model.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> findAll();

    Optional<Product> findById(Long id);
}
