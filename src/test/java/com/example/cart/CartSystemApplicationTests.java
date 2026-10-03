package com.example.cart;

import com.example.cart.business.service.AuthService;
import com.example.cart.business.service.CartService;
import com.example.cart.business.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class CartSystemApplicationTests {
	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private ProductService productService;

	@MockitoBean
	private CartService cartService;

	@Test
	void contextLoads() {
	}

}
