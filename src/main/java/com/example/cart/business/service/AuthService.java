package com.example.cart.business.service;

import com.example.cart.business.exception.AuthenticationException;
import com.example.cart.business.exception.UserAlreadyExistsException;
import com.example.cart.business.model.User;
import com.example.cart.business.repository.UserRepository;
import com.example.cart.business.security.PasswordHasher;
import com.example.cart.business.security.TokenService;

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public User register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UserAlreadyExistsException(username);
        }

        String hashedPassword = passwordHasher.hash(password);
        User user = new User(null, username, hashedPassword);
        return userRepository.save(user);
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(AuthenticationException::new);

        if (!passwordHasher.matches(password, user.getPassword())) {
            throw new AuthenticationException();
        }

        return tokenService.generateToken(user);
    }
}
