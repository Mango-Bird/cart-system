package com.example.cart.presentation.controller;

import com.example.cart.business.exception.ProductNotFoundException;
import com.example.cart.business.model.Product;
import com.example.cart.business.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getAllProductsReturnsProductResponseArray() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(
                new Product(5L, "Keyboard", new BigDecimal("49.90"), 12)));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("Keyboard"))
                .andExpect(jsonPath("$[0].price").value(49.90))
                .andExpect(jsonPath("$[0].stock").value(12));

        verify(productService).getAllProducts();
    }

    @Test
    void getProductByIdReturnsProductResponse() throws Exception {
        when(productService.getProductById(5L))
                .thenReturn(new Product(5L, "Keyboard", new BigDecimal("49.90"), 12));

        mockMvc.perform(get("/api/products/5"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(49.90))
                .andExpect(jsonPath("$.stock").value(12));

        verify(productService).getProductById(5L);
    }

    @Test
    void missingProductReturnsNotFoundErrorResponse() throws Exception {
        when(productService.getProductById(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found: 99"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        verify(productService).getProductById(99L);
    }
}
