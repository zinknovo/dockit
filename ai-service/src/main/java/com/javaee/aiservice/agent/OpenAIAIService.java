package com.javaee.aiservice.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class OpenAIAIService implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAIService.class);

    private final String apiKey;
    private final String baseUrl;
    private final String modelCode;
    private final String modelName;
    private final String apiModelName;
    private final boolean enabled;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAIAIService(String apiKey, String baseUrl, String modelCode, String modelName,
                           String apiModelName, boolean enabled) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelCode = modelCode;
        this.modelName = modelName;
        this.apiModelName = apiModelName;
        this.enabled = enabled;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    @Override
    public String callChat(String prompt) {
        if (!enabled) {
            throw new RuntimeException("Model " + modelName + " is disabled");
        }

        log.info("调用OpenAI兼容接口模型: {}, prompt长度: {}", modelName, prompt.length());

        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", apiModelName != null && !apiModelName.isEmpty() ? apiModelName : modelCode);
            requestBody.put("messages", new Object[]{message});

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // 解析响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    JsonNode messageNode = choices.get(0).get("message");
                    if (messageNode != null) {
                        String content = messageNode.get("content").asText();
                        log.info("{}响应成功", modelName);
                        return content;
                    }
                }
            }

            throw new RuntimeException("API返回结果为空或格式不正确");

        } catch (Exception e) {
            log.error("调用{}失败", modelName, e);
            throw new RuntimeException("调用" + modelName + "失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }
}
