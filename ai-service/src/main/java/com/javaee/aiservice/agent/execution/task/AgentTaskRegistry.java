package com.javaee.aiservice.agent.execution.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 任务运行注册表：保留最近一段时间（7 天）的 Agent 任务执行快照，
 * 支持按 traceId 查询、按 userId 列出、按审批 token 反查、取消标记等操作。
 * 单存储设计：注入 RedisTemplate 时以 Redis 为唯一事实源（无内存镜像，避免双写失同步）；
 * RedisTemplate 为 null（测试/无 Redis 环境）时退化为进程内内存存储。
 */
@Component
public class AgentTaskRegistry {

    private static final int MAX_TASKS = 200;
    private static final long TASK_TTL_DAYS = 7L;
    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final String TASK_ALL_ZSET = "agent:tasks:all";
    private static final String TASK_USER_ZSET_PREFIX = "agent:tasks:user:";
    private static final String TASK_TOKEN_INDEX_PREFIX = "agent:task:approval-token:";

    /** 内存模式（redisTemplate == null）专用：快照 + 审批 token 索引 + 淘汰队列 */
    private final ConcurrentMap<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> tokenToTraceId = new ConcurrentHashMap<>();
    private final java.util.Deque<String> evictionOrder = new ConcurrentLinkedDeque<>();

    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean redisMode;

    @Autowired(required = false)
    public AgentTaskRegistry(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisMode = redisTemplate != null;
    }

    public void save(String traceId, Map<String, Object> snapshot) {
        if (traceId == null || snapshot == null) {
            return;
        }
        Map<String, Object> stored = new LinkedHashMap<>(snapshot);
        stored.putIfAbsent("traceId", traceId);
        stored.putIfAbsent("savedAt", System.currentTimeMillis());
        if (redisMode) {
            persist(traceId, stored);
            indexApprovalToken(traceId, stored);
        } else {
            tasks.put(traceId, stored);
            indexApprovalTokenMemory(traceId, stored);
            evictionOrder.addLast(traceId);
            while (evictionOrder.size() > MAX_TASKS) {
                String oldest = evictionOrder.pollFirst();
                if (oldest != null) {
                    Map<String, Object> removed = tasks.remove(oldest);
                    removeTokenIndexMemory(oldest, removed);
                }
            }
        }
    }

    public Map<String, Object> get(String traceId) {
        if (redisMode) {
            return load(traceId);
        }
        Map<String, Object> snapshot = tasks.get(traceId);
        return snapshot == null ? Collections.emptyMap() : snapshot;
    }

    public Map<String, Object> findByApprovalToken(String token) {
        if (token == null || token.isBlank()) {
            return Collections.emptyMap();
        }
        if (redisMode) {
            Object indexed = redisTemplate.opsForValue().get(TASK_TOKEN_INDEX_PREFIX + token);
            if (indexed != null) {
                Map<String, Object> snapshot = load(String.valueOf(indexed));
                if (matchesToken(snapshot, token)) {
                    return snapshot;
                }
            }
            // 索引未命中时线性扫描兜底（兼容索引建立前的存量快照）
            for (Map<String, Object> snapshot : listAll()) {
                if (matchesToken(snapshot, token)) {
                    return snapshot;
                }
            }
            return Collections.emptyMap();
        }
        String indexedTraceId = tokenToTraceId.get(token);
        if (indexedTraceId != null) {
            Map<String, Object> snapshot = tasks.get(indexedTraceId);
            if (matchesToken(snapshot, token)) {
                return snapshot;
            }
        }
        for (Map<String, Object> snapshot : tasks.values()) {
            if (matchesToken(snapshot, token)) {
                return snapshot;
            }
        }
        return Collections.emptyMap();
    }

    public List<Map<String, Object>> listByUser(String userId) {
        if (userId == null) {
            return List.of();
        }
        if (redisMode) {
            return listFromRedis(TASK_USER_ZSET_PREFIX + userId);
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> snapshot : tasks.values()) {
            if (userId.equals(snapshot.get("userId"))) {
                matched.add(snapshot);
            }
        }
        matched.sort((a, b) -> Long.compare(asLong(b.get("savedAt")), asLong(a.get("savedAt"))));
        return matched;
    }

    public boolean cancel(String traceId) {
        Map<String, Object> snapshot = get(traceId);
        if (snapshot.isEmpty()) {
            return false;
        }
        Object status = snapshot.get("status");
        if ("success".equals(status) || "cancelled".equals(status) || "error".equals(status)) {
            return false;
        }
        snapshot.put("status", "cancelled");
        snapshot.put("cancelledAt", System.currentTimeMillis());
        if (redisMode) {
            persist(traceId, snapshot);
        }
        return true;
    }

    public boolean isCancelled(String traceId) {
        Map<String, Object> snapshot = get(traceId);
        return !snapshot.isEmpty() && "cancelled".equals(snapshot.get("status"));
    }

    /**
     * 列出全部任务（按 savedAt 倒序）。供管理员/调试使用。
     */
    public List<Map<String, Object>> listAll() {
        if (redisMode) {
            return listFromRedis(TASK_ALL_ZSET);
        }
        List<Map<String, Object>> all = new ArrayList<>(tasks.values());
        all.sort((a, b) -> Long.compare(asLong(b.get("savedAt")), asLong(a.get("savedAt"))));
        return all;
    }

    /**
     * 删除指定任务快照。
     */
    public boolean delete(String traceId) {
        if (redisMode) {
            return deletePersisted(traceId);
        }
        Map<String, Object> removed = tasks.remove(traceId);
        removeTokenIndexMemory(traceId, removed);
        evictionOrder.remove(traceId);
        return removed != null;
    }

    private void persist(String traceId, Map<String, Object> snapshot) {
        String key = TASK_KEY_PREFIX + traceId;
        long score = asLong(snapshot.get("savedAt"));
        redisTemplate.opsForValue().set(key, snapshot, TASK_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForZSet().add(TASK_ALL_ZSET, traceId, score);
        Object userId = snapshot.get("userId");
        if (userId != null) {
            redisTemplate.opsForZSet().add(TASK_USER_ZSET_PREFIX + userId, traceId, score);
        }
    }

    private void indexApprovalToken(String traceId, Map<String, Object> snapshot) {
        String token = approvalTokenOf(snapshot);
        if (token != null) {
            redisTemplate.opsForValue().set(TASK_TOKEN_INDEX_PREFIX + token, traceId, TASK_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    private void indexApprovalTokenMemory(String traceId, Map<String, Object> snapshot) {
        String token = approvalTokenOf(snapshot);
        if (token != null) {
            tokenToTraceId.put(token, traceId);
        }
    }

    private void removeTokenIndexMemory(String traceId, Map<String, Object> snapshot) {
        String token = approvalTokenOf(snapshot);
        if (token != null && traceId.equals(tokenToTraceId.get(token))) {
            tokenToTraceId.remove(token);
        }
    }

    private static String approvalTokenOf(Map<String, Object> snapshot) {
        Object pending = snapshot.get("pendingApproval");
        if (pending instanceof Map<?, ?> map && map.get("agentApprovalToken") != null) {
            return String.valueOf(map.get("agentApprovalToken"));
        }
        return null;
    }

    private static boolean matchesToken(Map<String, Object> snapshot, String token) {
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }
        Object pending = snapshot.get("pendingApproval");
        return pending instanceof Map<?, ?> map && token.equals(String.valueOf(map.get("agentApprovalToken")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String traceId) {
        if (traceId == null) {
            return Collections.emptyMap();
        }
        Object stored = redisTemplate.opsForValue().get(TASK_KEY_PREFIX + traceId);
        if (stored instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Collections.emptyMap();
    }

    private List<Map<String, Object>> listFromRedis(String zsetKey) {
        Set<Object> ids = redisTemplate.opsForZSet().reverseRange(zsetKey, 0, MAX_TASKS - 1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Object id : ids) {
            Map<String, Object> item = load(String.valueOf(id));
            if (!item.isEmpty()) {
                snapshots.add(item);
            }
        }
        snapshots.sort((a, b) -> Long.compare(asLong(b.get("savedAt")), asLong(a.get("savedAt"))));
        return snapshots;
    }

    private boolean deletePersisted(String traceId) {
        if (traceId == null) {
            return false;
        }
        Map<String, Object> snapshot = load(traceId);
        redisTemplate.delete(TASK_KEY_PREFIX + traceId);
        redisTemplate.opsForZSet().remove(TASK_ALL_ZSET, traceId);
        Object userId = snapshot.get("userId");
        if (userId != null) {
            redisTemplate.opsForZSet().remove(TASK_USER_ZSET_PREFIX + userId, traceId);
        }
        String token = approvalTokenOf(snapshot);
        if (token != null) {
            redisTemplate.delete(TASK_TOKEN_INDEX_PREFIX + token);
        }
        return true;
    }

    private long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
