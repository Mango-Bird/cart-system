package com.example.cart.presentation.controller;

import com.example.cart.business.exception.CartItemNotFoundException;
import com.example.cart.business.exception.CartNotFoundException;
import com.example.cart.business.exception.InsufficientStockException;
import com.example.cart.business.exception.InvalidCartItemException;
import com.example.cart.business.model.Cart;
import com.example.cart.business.model.CartItem;
import com.example.cart.business.service.CartService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    private static final Long USER_ID = 17L;
    private static final Long PRODUCT_ID = 23L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    void getCartPassesHeaderUserIdToService() throws Exception {
        when(cartService.getCartByUserId(USER_ID)).thenReturn(cartWithOneItem());

        mockMvc.perform(get("/api/cart").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.userId").value(17))
                .andExpect(jsonPath("$.items[0].productId").value(23))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        verify(cartService).getCartByUserId(USER_ID);
    }

    @Test
    void addItemPassesHeaderAndRequestFieldsToService() throws Exception {
        when(cartService.addItem(USER_ID, PRODUCT_ID, 2)).thenReturn(cartWithOneItem());

        mockMvc.perform(post("/api/cart/items")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":23,"quantity":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.userId").value(17))
                .andExpect(jsonPath("$.items[0].productId").value(23))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        verify(cartService).addItem(USER_ID, PRODUCT_ID, 2);
    }

    @Test
    void updateItemPassesHeaderPathAndRequestFieldsToService() throws Exception {
        when(cartService.updateItem(USER_ID, PRODUCT_ID, 4)).thenReturn(cartWithQuantity(4));

        mockMvc.perform(put("/api/cart/items/23")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4));

        verify(cartService).updateItem(USER_ID, PRODUCT_ID, 4);
    }

    @Test
    void deleteItemPassesHeaderAndPathProductIdToService() throws Exception {
        when(cartService.deleteItem(USER_ID, PRODUCT_ID)).thenReturn(new Cart(31L, USER_ID, List.of()));

        mockMvc.perform(delete("/api/cart/items/23").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.userId").value(17))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(cartService).deleteItem(USER_ID, PRODUCT_ID);
    }

    @Test
    void cartNotFoundReturnsNotFoundErrorResponse() throws Exception {
        when(cartService.getCartByUserId(USER_ID)).thenThrow(new CartNotFoundException(USER_ID));

        expectError(get("/api/cart"), 404, "Cart not found for user: 17");
        verify(cartService).getCartByUserId(USER_ID);
    }

    @Test
    void cartItemNotFoundReturnsNotFoundErrorResponse() throws Exception {
        when(cartService.deleteItem(USER_ID, PRODUCT_ID))
                .thenThrow(new CartItemNotFoundException(31L, PRODUCT_ID));

        expectError(delete("/api/cart/items/23"), 404,
                "Cart item not found for cart: 31, product: 23");
        verify(cartService).deleteItem(USER_ID, PRODUCT_ID);
    }

    @Test
    void invalidCartItemReturnsBadRequestErrorResponse() throws Exception {
        when(cartService.addItem(USER_ID, PRODUCT_ID, 0))
                .thenThrow(new InvalidCartItemException("Cart item quantity must be greater than zero"));

        expectError(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":23,\"quantity\":0}"), 400,
                "Cart item quantity must be greater than zero");
        verify(cartService).addItem(USER_ID, PRODUCT_ID, 0);
    }

    @Test
    void insufficientStockReturnsBadRequestErrorResponse() throws Exception {
        when(cartService.updateItem(USER_ID, PRODUCT_ID, 99))
                .thenThrow(new InsufficientStockException(PRODUCT_ID, 99, 8));

        expectError(put("/api/cart/items/23").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":99}"), 400,
                "Insufficient stock for product: 23 (requested: 99, available: 8)");
        verify(cartService).updateItem(USER_ID, PRODUCT_ID, 99);
    }

    private void expectError(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                             int statusCode, String message) throws Exception {
        mockMvc.perform(request.header("X-User-Id", USER_ID))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private static Cart cartWithOneItem() {
        return cartWithQuantity(2);
    }

    private static Cart cartWithQuantity(int quantity) {
        return new Cart(31L, USER_ID, List.of(new CartItem(44L, 31L, PRODUCT_ID, quantity)));
    }
}
