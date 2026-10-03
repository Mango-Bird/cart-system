package com.example.cart.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.cart.business.exception.AuthenticationException;
import com.example.cart.business.exception.UserAlreadyExistsException;
import com.example.cart.business.model.User;
import com.example.cart.business.repository.UserRepository;
import com.example.cart.business.security.PasswordHasher;
import com.example.cart.business.security.TokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String USERNAME = "bird";
    private static final String PASSWORD = "plain-password";
    private static final String HASHED_PASSWORD = "hashed-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordHasher, tokenService);
    }

    @Test
    void registerHashesPasswordAndSavesUser() {
        User savedUser = new User(12L, USERNAME, HASHED_PASSWORD);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
        when(passwordHasher.hash(PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = authService.register(USERNAME, PASSWORD);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(passwordHasher).hash(PASSWORD);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(USERNAME, userCaptor.getValue().getUsername());
        assertEquals(HASHED_PASSWORD, userCaptor.getValue().getPassword());
        assertFalse(PASSWORD.equals(userCaptor.getValue().getPassword()));
        assertSame(savedUser, result);
    }

    @Test
    void registerRejectsExistingUsernameWithoutHashingOrSaving() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new User(1L, USERNAME, HASHED_PASSWORD)));

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(USERNAME, PASSWORD));

        verifyNoInteractions(passwordHasher);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = new User(12L, USERNAME, HASHED_PASSWORD);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordHasher.matches(PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(tokenService.generateToken(user)).thenReturn("token-value");

        String result = authService.login(USERNAME, PASSWORD);

        assertEquals("token-value", result);
        verify(passwordHasher).matches(PASSWORD, HASHED_PASSWORD);
        verify(tokenService).generateToken(user);
    }

    @Test
    void loginRejectsUnknownUserWithoutGeneratingToken() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class, () -> authService.login(USERNAME, PASSWORD));

        verifyNoInteractions(passwordHasher, tokenService);
    }

    @Test
    void loginRejectsIncorrectPasswordWithoutGeneratingToken() {
        User user = new User(12L, USERNAME, HASHED_PASSWORD);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordHasher.matches(PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThrows(AuthenticationException.class, () -> authService.login(USERNAME, PASSWORD));

        verify(tokenService, never()).generateToken(any(User.class));
    }
}
