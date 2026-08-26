package com.javaee.aiservice.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 向量存储：基于 Qdrant（独立向量数据库，REST API）。
 * - collection 首次写入时按向量实际维度创建（Cosine 距离）
 * - 内部 id 映射为确定性 UUID（UUID.nameUUIDFromBytes），原始 id 存入 payload.rawId
 * - 检索按 payload 等值过滤（filters 中值为空的键忽略）
 */
@Component
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);
    private static final String COLLECTION = "dockit-docs";

    @Value("${ai.vector.qdrant-url:http://localhost:6333}")
    private String qdrantUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 首次写入时按实际向量维度创建 collection，之后复用 */
    private volatile int collectionDimension = -1;

    /**
     * 存储向量（首次写入时自动创建 collection）
     */
    public void store(String id, float[] vector, Map<String, Object> metadata) {
        log.info("存储向量: id={}, dimension={}", id, vector.length);
        try {
            ensureCollection(vector.length);

            Map<String, Object> payload = new HashMap<>(metadata);
            payload.put("rawId", id);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", toUuid(id));
            point.put("vector", toDoubleList(vector));
            point.put("payload", payload);

            Map<String, Object> body = new HashMap<>();
            body.put("points", List.of(point));

            String response = put("/collections/" + COLLECTION + "/points?wait=true", body);
            log.info("向量存储成功: id={}, qdrant响应={}", id, response);
        } catch (Exception e) {
            log.error("向量存储失败", e);
            throw new RuntimeException("向量存储失败: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> search(float[] queryVector, int topK) {
        return search(queryVector, topK, Collections.emptyMap());
    }

    public List<Map<String, Object>> search(float[] queryVector, int topK, Map<String, Object> filters) {
        log.info("搜索相似向量: topK={}", topK);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vector", toDoubleList(queryVector));
            body.put("limit", Math.max(topK, 1));
            body.put("with_payload", true);
            if (filters != null && !filters.isEmpty()) {
                body.put("filter", buildFilter(filters));
            }

            String response = post("/collections/" + COLLECTION + "/points/search", body);
            JsonNode root = objectMapper.readTree(response);
            JsonNode points = root.path("result");
            if (!points.isArray()) {
                log.warn("Qdrant 搜索返回异常: {}", response);
                return Collections.emptyList();
            }

            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode point : points) {
                JsonNode payload = point.path("payload");
                String rawId = payload.path("rawId").asText(null);
                if (rawId == null) {
                    continue;
                }
                float similarity = (float) point.path("score").asDouble(0.0);

                Map<String, Object> item = new HashMap<>();
                item.put("id", rawId);
                item.put("similarity", similarity);
                // 与 scrollAll 同一套解析：数字/布尔保持原生类型，不做字符串化
                Map<String, Object> flat = flattenPayload(payload);
                flat.remove("rawId");
                item.putAll(flat);
                results.add(item);
            }

            log.info("搜索完成，找到{}个结果", results.size());
            return results;
        } catch (Exception e) {
            log.error("向量搜索失败", e);
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }

    public void delete(String id) {
        log.info("删除向量: id={}", id);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("points", List.of(toUuid(id)));
            post("/collections/" + COLLECTION + "/points/delete", body);
            log.info("向量删除成功: id={}", id);
        } catch (Exception e) {
            log.error("向量删除失败", e);
            throw new RuntimeException("向量删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取向量的 payload（元数据），供 BM25 等非向量检索复用过滤
     */
    public Map<String, Object> getMetadata(String id) {
        return getPayload(id);
    }

    /**
     * 读取点的完整 payload（保留字段类型：字符串/数字/数组），供原文与分段映射读取
     */
    public Map<String, Object> getPayload(String id) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("ids", List.of(toUuid(id)));
            body.put("with_payload", true);
            String response = post("/collections/" + COLLECTION + "/points", body);
            JsonNode points = objectMapper.readTree(response).path("result");
            if (points.isArray() && points.size() > 0) {
                return flattenPayload(points.get(0).path("payload"));
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("读取向量元数据失败: id={}: {}", id, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 滚动读取全部点的 payload（含 rawId），供 BM25 / 统计等全量扫描场景
     */
    public List<Map<String, Object>> scrollAll() {
        return scrollAll(Collections.emptyMap());
    }

    /**
     * 按 payload 等值过滤全量扫描（服务端过滤，避免拉回全部点再在内存里筛）。
     */
    public List<Map<String, Object>> scrollAll(Map<String, Object> filters) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("limit", 10000);
            body.put("with_payload", true);
            if (filters != null && !filters.isEmpty()) {
                body.put("filter", buildFilter(filters));
            }
            String response = post("/collections/" + COLLECTION + "/points/scroll", body);
            JsonNode points = objectMapper.readTree(response).path("result").path("points");
            List<Map<String, Object>> all = new ArrayList<>();
            if (points.isArray()) {
                for (JsonNode point : points) {
                    Map<String, Object> payload = flattenPayload(point.path("payload"));
                    String rawId = point.path("payload").path("rawId").asText(null);
                    if (rawId != null) {
                        payload.put("rawId", rawId);
                        all.add(payload);
                    }
                }
            }
            return all;
        } catch (Exception e) {
            log.warn("Qdrant 全量扫描失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> flattenPayload(JsonNode payload) {
        Map<String, Object> result = new HashMap<>();
        if (payload == null || !payload.isObject()) {
            return result;
        }
        payload.fieldNames().forEachRemaining(field -> {
            JsonNode value = payload.path(field);
            if (value.isTextual()) {
                result.put(field, value.asText());
            } else if (value.isNumber()) {
                result.put(field, value.asDouble());
            } else if (value.isBoolean()) {
                result.put(field, value.asBoolean());
            } else if (value.isArray()) {
                List<String> items = new ArrayList<>();
                value.forEach(item -> items.add(item.asText()));
                result.put(field, items);
            } else if (value.isObject()) {
                result.put(field, value.toString());
            }
        });
        return result;
    }

    /**
     * 创建 collection（幂等：已存在则忽略）。Qdrant 需要固定维度，取首次写入向量的实际维度。
     */
    private synchronized void ensureCollection(int dimension) {
        if (collectionDimension == dimension) {
            return;
        }
        try {
            Map<String, Object> vectors = new HashMap<>();
            vectors.put("size", dimension);
            vectors.put("distance", "Cosine");
            Map<String, Object> body = new HashMap<>();
            body.put("vectors", vectors);
            put("/collections/" + COLLECTION, body);
            collectionDimension = dimension;
            log.info("Qdrant collection 已就绪: {}, dimension={}", COLLECTION, dimension);
        } catch (Exception e) {
            // collection 已存在时 Qdrant 返回 409，视为就绪
            log.info("Qdrant collection 可能已存在: {}", e.getMessage());
            collectionDimension = dimension;
        }
    }

    private Map<String, Object> buildFilter(Map<String, Object> filters) {
        List<Map<String, Object>> must = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object value = entry.getValue();
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            Map<String, Object> match = new HashMap<>();
            match.put("value", value.toString());
            Map<String, Object> condition = new HashMap<>();
            condition.put("key", entry.getKey());
            condition.put("match", match);
            must.add(condition);
        }
        Map<String, Object> filter = new HashMap<>();
        filter.put("must", must);
        return filter;
    }

    private String toUuid(String id) {
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private List<Double> toDoubleList(float[] vector) {
        List<Double> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add((double) v);
        }
        return list;
    }

    private String put(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(qdrantUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return send(request);
    }

    private String post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(qdrantUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return send(request);
    }

    private String send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Qdrant HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }
}
