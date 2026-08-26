package com.javaee.aiservice.agent.execution.task;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 模式：Redis 是唯一事实源（无内存镜像），审批 token 走索引反查。
 */
class AgentTaskRegistryTest {

    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, Object> zsetOps = mock(ZSetOperations.class);

    private AgentTaskRegistry registry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        return new AgentTaskRegistry(redisTemplate);
    }

    private Map<String, Object> snapshot(String userId, String approvalToken) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("userId", userId);
        snapshot.put("status", "running");
        if (approvalToken != null) {
            snapshot.put("pendingApproval", Map.of("agentApprovalToken", approvalToken));
        }
        return snapshot;
    }

    @Test
    void savePersistsSnapshotZsetAndApprovalTokenIndex() {
        AgentTaskRegistry registry = registry();

        registry.save("trace-1", snapshot("alice", "tok-1"));

        verify(valueOps).set(eq("agent:task:trace-1"), any(), eq(7L), eq(TimeUnit.DAYS));
        verify(zsetOps).add(eq("agent:tasks:all"), eq("trace-1"), anyDouble());
        verify(zsetOps).add(eq("agent:tasks:user:alice"), eq("trace-1"), anyDouble());
        verify(valueOps).set(eq("agent:task:approval-token:tok-1"), eq("trace-1"), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void saveWithoutPendingApprovalWritesNoTokenIndex() {
        AgentTaskRegistry registry = registry();

        registry.save("trace-1", snapshot("alice", null));

        verify(valueOps, never()).set(eq("agent:task:approval-token:tok-1"), any(), anyLong(), any());
    }

    @Test
    void findByApprovalTokenUsesIndexWithoutScanningAll() {
        AgentTaskRegistry registry = registry();
        when(valueOps.get("agent:task:approval-token:tok-1")).thenReturn("trace-1");
        when(valueOps.get("agent:task:trace-1")).thenReturn(snapshot("alice", "tok-1"));

        Map<String, Object> found = registry.findByApprovalToken("tok-1");

        assertThat(found).containsEntry("userId", "alice");
        verify(zsetOps, never()).reverseRange(any(), anyLong(), anyLong());
    }

    @Test
    void findByApprovalTokenFallsBackToScanWhenIndexMissing() {
        AgentTaskRegistry registry = registry();
        when(valueOps.get("agent:task:approval-token:tok-1")).thenReturn(null);
        when(zsetOps.reverseRange("agent:tasks:all", 0, 199)).thenReturn(Set.of("trace-1"));
        when(valueOps.get("agent:task:trace-1")).thenReturn(snapshot("alice", "tok-1"));

        Map<String, Object> found = registry.findByApprovalToken("tok-1");

        assertThat(found).containsEntry("userId", "alice");
    }

    @Test
    void findByApprovalTokenRejectsStaleIndexEntry() {
        AgentTaskRegistry registry = registry();
        when(valueOps.get("agent:task:approval-token:tok-1")).thenReturn("trace-1");
        // 索引指向的旧快照已无 pendingApproval（审批已消费），应返回空而非误命中
        when(valueOps.get("agent:task:trace-1")).thenReturn(snapshot("alice", null));

        assertThat(registry.findByApprovalToken("tok-1")).isEmpty();
    }

    @Test
    void cancelUpdatesSnapshotInRedis() {
        AgentTaskRegistry registry = registry();
        // 模拟 Redis 行为：get 返回最近一次 set 的快照
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> stored =
                new java.util.concurrent.atomic.AtomicReference<>(snapshot("alice", "tok-1"));
        when(valueOps.get("agent:task:trace-1")).thenAnswer(inv -> stored.get());
        org.mockito.stubbing.Answer<Object> capture = inv -> {
            stored.set(inv.getArgument(1));
            return null;
        };
        org.mockito.Mockito.doAnswer(capture).when(valueOps)
                .set(eq("agent:task:trace-1"), any(), eq(7L), eq(TimeUnit.DAYS));

        assertThat(registry.cancel("trace-1")).isTrue();

        assertThat(registry.isCancelled("trace-1")).isTrue();
        assertThat(registry.cancel("trace-1")).isFalse();
    }

    @Test
    void deleteRemovesTaskAndApprovalTokenIndex() {
        AgentTaskRegistry registry = registry();
        when(valueOps.get("agent:task:trace-1")).thenReturn(snapshot("alice", "tok-1"));

        assertThat(registry.delete("trace-1")).isTrue();

        verify(redisTemplate).delete("agent:task:trace-1");
        verify(redisTemplate).delete("agent:task:approval-token:tok-1");
        verify(zsetOps).remove("agent:tasks:all", "trace-1");
        verify(zsetOps).remove("agent:tasks:user:alice", "trace-1");
    }

    @Test
    void memoryModeStillSupportsTokenLookupAndCancel() {
        AgentTaskRegistry registry = new AgentTaskRegistry(null);
        Map<String, Object> snap = snapshot("bob", "tok-mem");
        registry.save("trace-1", snap);

        assertThat(registry.findByApprovalToken("tok-mem")).containsEntry("userId", "bob");
        assertThat(registry.listByUser("bob")).hasSize(1);
        assertThat(registry.listAll()).hasSize(1);
        assertThat(registry.cancel("trace-1")).isTrue();
        assertThat(registry.isCancelled("trace-1")).isTrue();
        assertThat(registry.delete("trace-1")).isTrue();
        assertThat(registry.get("trace-1")).isEmpty();
    }
}
