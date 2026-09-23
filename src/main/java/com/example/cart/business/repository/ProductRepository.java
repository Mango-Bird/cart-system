package com.example.cart.business.repository;

import com.example.cart.business.model.Product;
import java.util.List;

public interface ProductRepository {
    Product findById(Long id);
    List<Product> findAll();
    Product save(Product product);
}
