package com.example.cart.infrastructure.cache;

import com.example.cart.business.cache.CartCache;
import com.example.cart.business.model.Cart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

public final class RedisCartCache implements CartCache {

    private static final Logger logger =
            LoggerFactory.getLogger(RedisCartCache.class);

    private static final String KEY_PREFIX = "cart:user:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCartCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Cart get(Long userId) {
        try {
            String json = redisTemplate.opsForValue().get(key(userId));

            if (json == null) {
                return null;
            }

            return objectMapper.readValue(json, Cart.class);
        } catch (JacksonException | DataAccessException exception) {
            logger.warn(
                    "Could not read cart cache for user " + userId,
                    exception
            );
            return null;
        }
    }

    @Override
    public void put(Long userId, Cart cart) {
        try {
            String json = objectMapper.writeValueAsString(cart);

            redisTemplate.opsForValue().set(
                    key(userId),
                    json,
                    TTL
            );
        } catch (JacksonException | DataAccessException exception) {
            logger.warn(
                    "Could not write cart cache for user " + userId,
                    exception
            );
        }
    }

    @Override
    public void evict(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException exception) {
            logger.warn(
                    "Could not evict cart cache for user " + userId,
                    exception
            );
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}