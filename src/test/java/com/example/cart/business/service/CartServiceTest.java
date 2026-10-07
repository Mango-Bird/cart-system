package com.example.cart.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.cart.business.cache.CartCache;
import com.example.cart.business.exception.CartItemNotFoundException;
import com.example.cart.business.exception.CartNotFoundException;
import com.example.cart.business.exception.InsufficientStockException;
import com.example.cart.business.exception.InvalidCartItemException;
import com.example.cart.business.exception.ProductNotFoundException;
import com.example.cart.business.model.Cart;
import com.example.cart.business.model.CartItem;
import com.example.cart.business.model.Product;
import com.example.cart.business.repository.CartItemRepository;
import com.example.cart.business.repository.CartRepository;
import com.example.cart.business.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    private static final Long USER_ID = 3L;
    private static final Long CART_ID = 7L;
    private static final Long PRODUCT_ID = 11L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartCache cartCache;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, productRepository, cartCache);
    }

    @Test
    void getCartByUserIdReturnsCachedCartWithoutRepositoryLookup() {
        Cart cart = cart();
        when(cartCache.get(USER_ID)).thenReturn(cart);

        assertSame(cart, cartService.getCartByUserId(USER_ID));

        verify(cartRepository, never()).findByUserId(USER_ID);
    }

    @Test
    void getCartByUserIdLoadsAndCachesOnMiss() {
        Cart cart = cart();
        when(cartCache.get(USER_ID)).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertSame(cart, cartService.getCartByUserId(USER_ID));

        verify(cartRepository).findByUserId(USER_ID);
        verify(cartCache).put(USER_ID, cart);
    }

    @Test
    void getCartByUserIdCreatesAndCachesEmptyCartWhenCartDoesNotExist() {
        Cart savedCart = new Cart(
                CART_ID,
                USER_ID,
                List.of()
        );

        when(cartCache.get(USER_ID)).thenReturn(null);
        when(cartRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class)))
                .thenReturn(savedCart);

        Cart result = cartService.getCartByUserId(USER_ID);

        ArgumentCaptor<Cart> cartCaptor =
                ArgumentCaptor.forClass(Cart.class);

        verify(cartRepository).save(cartCaptor.capture());

        Cart newCart = cartCaptor.getValue();

        assertNull(newCart.getId());
        assertEquals(USER_ID, newCart.getUserId());
        assertTrue(newCart.getItems().isEmpty());

        assertSame(savedCart, result);
        verify(cartCache).put(USER_ID, savedCart);
    }

    @Test
    void addItemRejectsNonPositiveQuantityBeforeRepositoryCalls() {
        assertThrows(InvalidCartItemException.class, () -> cartService.addItem(USER_ID, PRODUCT_ID, 0));

        verifyNoInteractions(cartRepository, cartItemRepository, productRepository);
    }

    @Test
    void addItemThrowsWhenProductDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> cartService.addItem(USER_ID, PRODUCT_ID, 1));

        verify(cartRepository, never()).findByUserId(USER_ID);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void addItemRejectsQuantityOverStockWithoutSavingItem() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(2)));

        assertThrows(InsufficientStockException.class, () -> cartService.addItem(USER_ID, PRODUCT_ID, 3));

        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(cartCache, never()).evict(USER_ID);
    }

    @Test
    void addItemCreatesCartAndItemThenEvictsCache() {
        Product product = product(8);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart newCart = invocation.getArgument(0);
            assertNull(newCart.getId());
            assertEquals(USER_ID, newCart.getUserId());
            assertTrue(newCart.getItems().isEmpty());
            newCart.setId(CART_ID);
            return newCart;
        });
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.addItem(USER_ID, PRODUCT_ID, 2);

        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertEquals(CART_ID, cartCaptor.getValue().getId());
        assertEquals(USER_ID, cartCaptor.getValue().getUserId());

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        assertNull(itemCaptor.getValue().getId());
        assertEquals(CART_ID, itemCaptor.getValue().getCartId());
        assertEquals(PRODUCT_ID, itemCaptor.getValue().getProductId());
        assertEquals(2, itemCaptor.getValue().getQuantity());
        verify(cartCache).evict(USER_ID);
        assertSame(cartCaptor.getValue(), result);
        assertEquals(1, result.getItems().size());
        assertEquals(PRODUCT_ID, result.getItems().get(0).getProductId());
        assertEquals(2, result.getItems().get(0).getQuantity());
    }

    @Test
    void addItemIncrementsExistingItemQuantityAndEvictsCache() {
        Product product = product(10);
        CartItem existingItem = cartItem(3);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID))
                .thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(existingItem)).thenReturn(existingItem);

        Cart result = cartService.addItem(USER_ID, PRODUCT_ID, 4);

        assertEquals(7, existingItem.getQuantity());
        assertEquals(1, result.getItems().size());
        assertEquals(7, result.getItems().get(0).getQuantity());
        verify(cartItemRepository).save(existingItem);
        verify(cartCache).evict(USER_ID);
    }

    @Test
    void addItemRejectsFinalQuantityOverStockForExistingItem() {
        Product product = product(6);
        CartItem existingItem = cartItem(4);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID))
                .thenReturn(Optional.of(existingItem));

        assertThrows(InsufficientStockException.class, () -> cartService.addItem(USER_ID, PRODUCT_ID, 3));

        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(cartCache, never()).evict(USER_ID);
    }

    @Test
    void updateItemRejectsNonPositiveQuantityBeforeRepositoryCalls() {
        assertThrows(InvalidCartItemException.class, () -> cartService.updateItem(USER_ID, PRODUCT_ID, 0));

        verifyNoInteractions(productRepository, cartRepository, cartItemRepository);
    }

    @Test
    void updateItemThrowsWhenProductDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> cartService.updateItem(USER_ID, PRODUCT_ID, 1));

        verify(cartRepository, never()).findByUserId(USER_ID);
    }

    @Test
    void updateItemThrowsWhenUserCartDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.updateItem(USER_ID, PRODUCT_ID, 1));

        verify(cartItemRepository, never()).findByCartIdAndProductId(any(), any());
    }

    @Test
    void updateItemThrowsWhenItemDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(CartItemNotFoundException.class,
                () -> cartService.updateItem(USER_ID, PRODUCT_ID, 1));

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void updateItemRejectsQuantityOverStock() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(2)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID))
                .thenReturn(Optional.of(cartItem(1)));

        assertThrows(InsufficientStockException.class,
                () -> cartService.updateItem(USER_ID, PRODUCT_ID, 3));

        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(cartCache, never()).evict(USER_ID);
    }

    @Test
    void updateItemSavesQuantityAndEvictsCache() {
        CartItem item = cartItem(1);
        Cart cart = new Cart(CART_ID, USER_ID, List.of(item));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(10)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID))
                .thenReturn(Optional.of(item));
        when(cartItemRepository.save(item)).thenReturn(item);

        Cart result = cartService.updateItem(USER_ID, PRODUCT_ID, 5);

        assertSame(cart, result);
        assertEquals(5, item.getQuantity());
        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
        verify(cartItemRepository).save(item);
        verify(cartCache).evict(USER_ID);
    }

    @Test
    void deleteItemThrowsWhenUserCartDoesNotExist() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.deleteItem(USER_ID, PRODUCT_ID));

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void deleteItemThrowsWhenItemDoesNotExist() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(CartItemNotFoundException.class, () -> cartService.deleteItem(USER_ID, PRODUCT_ID));

        verify(cartItemRepository, never()).delete(any(CartItem.class));
        verify(cartCache, never()).evict(USER_ID);
    }

    @Test
    void deleteItemRemovesItemFromReturnedCartAndEvictsCache() {
        CartItem item = cartItem(1);
        Cart cart = new Cart(CART_ID, USER_ID, List.of(item));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID))
                .thenReturn(Optional.of(item));

        Cart result = cartService.deleteItem(USER_ID, PRODUCT_ID);

        verify(cartItemRepository).delete(item);
        assertSame(cart, result);
        assertFalse(result.getItems().contains(item));
        verify(cartCache).evict(USER_ID);
    }

    private Product product(int stock) {
        return new Product(PRODUCT_ID, "Widget", BigDecimal.valueOf(10), stock);
    }

    private Cart cart() {
        return new Cart(CART_ID, USER_ID, List.of());
    }

    private CartItem cartItem(int quantity) {
        return new CartItem(13L, CART_ID, PRODUCT_ID, quantity);
    }
}
