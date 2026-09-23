package com.example.cart.business.repository;

import com.example.cart.business.model.User;

public interface UserRepository {
    User findById(Long id);
    User findByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);
}
