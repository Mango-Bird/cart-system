package com.example.cart.business.service;

import com.example.cart.business.model.Product;
import java.util.List;

public interface ProductService {
    List<Product> getAllProducts();
    Product getProductById(Long id);
}
