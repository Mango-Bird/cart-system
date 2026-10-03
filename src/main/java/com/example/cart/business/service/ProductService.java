package com.example.cart.business.service;

import com.example.cart.business.model.Product;
import com.example.cart.business.exception.ProductNotFoundException;
import com.example.cart.business.repository.ProductRepository;
import java.util.List;
import java.util.Objects;

public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
