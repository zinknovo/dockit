package com.javaee.aiservice.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档向量化器
 * 负责将文档内容转换为向量表示
 * 通过 OpenAI 兼容 embeddings 端点调用（当前指向火山方舟 doubao-embedding-vision，
 * 也兼容 DashScope /compatible-mode 等一切 OpenAI 兼容 embedding 服务）
 */
@Component
public class DocumentVectorizer {

    private static final Logger log = LoggerFactory.getLogger(DocumentVectorizer.class);

    @Value("${spring.ai.embedding.api-key}")
    private String apiKey;

    @Value("${spring.ai.embedding.base-url:https://ark.cn-beijing.volces.com/api/plan/v3}")
    private String baseUrl;

    @Value("${spring.ai.embedding.model:doubao-embedding-vision}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将文本向量化
     * 429（限流）或 5xx 时指数退避重试，最多 3 次
     * @param text 文本内容
     * @return 向量表示
     */
    public float[] vectorize(String text) {
        float[][] batch = vectorizeBatchInternal(new String[]{text});
        return batch[0];
    }

    /** 单次请求允许的最大 input 条数（火山方舟 embedding API 上限 10） */
    private static final int BATCH_LIMIT = 10;

    /**
     * 每秒 token 速率上限（令牌调度器）：2026-08-26 实测校准（火山方舟 doubao-embedding-vision）。
     * 限流根因：滑动窗口 token 速率（实测套餐档 ≈4000 token/s；官方模型吞吐 11000 token/s，
     * 730 token/s 的慢速流量畅通、28k token/s 的压测节奏全量 429），与累计额度、RPM 均无关。
     * 默认 1000 token/s 安全线（远低于实测线）；可在控制台/实测确认后调高该配置。
     */
    @Value("${spring.ai.embedding.tokens-per-second:1000}")
    private long tokensPerSecond;

    /** 全局令牌调度：下一次可用时刻（绝对时间戳） */
    private static final java.util.concurrent.atomic.AtomicLong NEXT_AVAILABLE_AT = new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * 批量向量化：按 BATCH_LIMIT 分片逐请求携带多个文本（input 数组），
     * 避免逐条调用触发账户 QPS 限流，同时不超单次请求条数上限。
     * @param texts 文本列表
     * @return 向量列表，与 texts 一一对应
     */
    public float[][] vectorizeBatch(String[] texts) {
        return vectorizeBatchInternal(texts);
    }

    private float[][] vectorizeBatchInternal(String[] texts) {
        log.info("批量向量化，数量={}", texts.length);
        if (texts.length == 0) {
            return new float[0][];
        }

        float[][] result = new float[texts.length][];
        for (int offset = 0; offset < texts.length; offset += BATCH_LIMIT) {
            int to = Math.min(offset + BATCH_LIMIT, texts.length);
            String[] slice = new String[to - offset];
            System.arraycopy(texts, offset, slice, 0, to - offset);
            float[][] sliceVectors = vectorizeSliceWithRetry(slice);
            System.arraycopy(sliceVectors, 0, result, offset, sliceVectors.length);
        }
        log.info("批量向量化完成，数量={}", result.length);
        return result;
    }

    private float[][] vectorizeSliceWithRetry(String[] texts) {
        int maxAttempts = 3;
        for (int attempt = 0; ; attempt++) {
            try {
                return doVectorizeBatch(texts);
            } catch (Exception e) {
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    log.error("调用Embedding API失败（已重试{}次）", attempt, e);
                    throw new RuntimeException("批量向量化失败: " + e.getMessage(), e);
                }
                long waitMs = 1000L * (attempt + 1);
                log.warn("Embedding API限流/服务不可用，{}ms 后重试 (第{}次): {}", waitMs, attempt + 1, e.getMessage());
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("批量向量化被中断", ie);
                }
            }
        }
    }

    /**
     * 429（限流）与 5xx（服务端瞬时故障）可重试；4xx 其余不可重试。
     * 按状态码判定而非消息字符串（消息里 "500 ..." 匹配不到 " 5" 这类脆弱子串）。
     */
    private boolean isRetryable(Exception e) {
        if (e instanceof HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        String message = e.getMessage();
        return message != null && (message.contains("429") || message.contains("Too Many"));
    }

    /**
     * 进程级 token 速率调度器（令牌桶，无突发容量）：按 tokensPerSecond 平铺所有请求，
     * 每个请求按估算 token 数（中文 1 字 ≈ 1 token，字符数 × 1.2 余量）占用相应的时段，
     * 并发调用经 CAS 串行化——整体速率恒定，永不撞滑动窗口限流。
     */
    private void throttle(String[] texts) {
        long estimatedTokens = 0;
        for (String text : texts) {
            estimatedTokens += text == null ? 0 : text.length();
        }
        estimatedTokens = Math.max(1, estimatedTokens * 12 / 10);
        long durationMs = Math.max(1, (long) Math.ceil((double) estimatedTokens * 1000 / tokensPerSecond));

        long now = System.currentTimeMillis();
        long startAt;
        while (true) {
            long cur = NEXT_AVAILABLE_AT.get();
            long myStart = Math.max(now, cur);
            if (NEXT_AVAILABLE_AT.compareAndSet(cur, myStart + durationMs)) {
                startAt = myStart;
                break;
            }
        }
        if (startAt > now) {
            try {
                Thread.sleep(startAt - now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private float[][] doVectorizeBatch(String[] texts) throws Exception {
        throttle(texts);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        // input 支持数组：一次请求携带全部文本
        requestBody.put("input", List.of(texts));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String url = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");
            if (data != null && data.isArray() && data.size() >= texts.length) {
                float[][] embeddings = new float[texts.length][];
                for (int i = 0; i < texts.length; i++) {
                    JsonNode embeddingNode = data.get(i).get("embedding");
                    if (embeddingNode == null || !embeddingNode.isArray()) {
                        throw new RuntimeException("Embedding API返回第" + i + "个结果为空或格式不正确");
                    }
                    List<Float> embeddingList = new ArrayList<>(embeddingNode.size());
                    for (JsonNode value : embeddingNode) {
                        embeddingList.add((float) value.asDouble());
                    }
                    float[] embedding = new float[embeddingList.size()];
                    for (int j = 0; j < embeddingList.size(); j++) {
                        embedding[j] = embeddingList.get(j);
                    }
                    embeddings[i] = embedding;
                }
                log.info("解析到向量维度: {}", embeddings[0].length);
                return embeddings;
            }
        }

        throw new RuntimeException("Embedding API返回结果为空或格式不正确");
    }

}
