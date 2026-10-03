package com.example.cart.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cart.business.exception.ProductNotFoundException;
import com.example.cart.business.model.Product;
import com.example.cart.business.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void getAllProductsReturnsRepositoryResults() {
        List<Product> products = List.of(product(1L), product(2L));
        when(productRepository.findAll()).thenReturn(products);

        List<Product> result = productService.getAllProducts();

        assertSame(products, result);
        verify(productRepository).findAll();
    }

    @Test
    void getProductByIdReturnsProductWhenFound() {
        Product product = product(7L);
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));

        Product result = productService.getProductById(7L);

        assertSame(product, result);
        verify(productRepository).findById(7L);
    }

    @Test
    void getProductByIdThrowsWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProductById(99L));

        assertEquals("Product not found: 99", exception.getMessage());
        verify(productRepository).findById(99L);
    }

    private Product product(Long id) {
        return new Product(id, "Product " + id, BigDecimal.valueOf(10), 20);
    }
}
