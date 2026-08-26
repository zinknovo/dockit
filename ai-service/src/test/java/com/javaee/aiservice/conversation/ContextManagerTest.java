package com.javaee.aiservice.conversation;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上下文 TTL 纪律：每次写入 ctx: 都要刷新过期时间，与 Conversation 同一生命周期。
 */
class ContextManagerTest {

    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private ContextManager manager() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        ContextManager manager = new ContextManager(redisTemplate);
        ReflectionTestUtils.setField(manager, "expiryHours", 24);
        return manager;
    }

    @Test
    void updateContextRefreshesTtl() {
        ContextManager manager = manager();

        manager.updateContext("conv-1", Map.of("answer", "ok"));

        verify(hashOps).putAll(eq("ctx:conv-1"), any());
        verify(redisTemplate).expire(eq("ctx:conv-1"), eq(Duration.ofHours(24)));
    }

    @Test
    void setContextValueRefreshesTtl() {
        ContextManager manager = manager();

        manager.setContextValue("conv-1", "answer", "ok");

        verify(hashOps).put(eq("ctx:conv-1"), eq("answer"), eq("ok"));
        verify(redisTemplate).expire(eq("ctx:conv-1"), eq(Duration.ofHours(24)));
    }

    @Test
    void mergeContextReplacesHashAndRefreshesTtl() {
        ContextManager manager = manager();
        when(hashOps.entries("ctx:conv-1")).thenReturn(new HashMap<>(Map.of("old", "value")));

        manager.mergeContext("conv-1", Map.of("answer", "ok"));

        verify(redisTemplate).delete("ctx:conv-1");
        verify(hashOps).putAll(eq("ctx:conv-1"), any());
        verify(redisTemplate).expire(eq("ctx:conv-1"), eq(Duration.ofHours(24)));
    }

    @Test
    void emptyUpdatesDoNotTouchRedis() {
        ContextManager manager = manager();

        manager.updateContext("conv-1", Map.of());
        manager.mergeContext("conv-1", null);

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }
}
