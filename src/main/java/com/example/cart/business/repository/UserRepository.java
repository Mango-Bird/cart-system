package com.example.cart.business.repository;

import com.example.cart.business.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);

    User save(User user);
}
