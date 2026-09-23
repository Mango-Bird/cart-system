package com.example.cart.business.model;

import java.util.ArrayList;
import java.util.List;

public class Cart {
    private Long id;
    private Long userId;
    private List<CartItem> items = new ArrayList<>();

    public Cart() {
    }

    public Cart(Long id, Long userId, List<CartItem> items) {
        this.id = id;
        this.userId = userId;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items == null ? new ArrayList<>() : new ArrayList<>(items); }
}
