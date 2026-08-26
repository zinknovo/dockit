package com.javaee.aiservice.agent;

import com.javaee.aiservice.factory.AIServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 自定义Chat服务
 * 支持多模型选择（模型清单由 spring.ai.models.list 配置驱动）
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final AIServiceFactory aiServiceFactory;

    @Autowired
    public ChatService(AIServiceFactory aiServiceFactory) {
        this.aiServiceFactory = aiServiceFactory;
    }

    /**
     * 调用默认模型
     * @param prompt 用户提示词
     * @return 响应内容
     */
    public String callChatApi(String prompt) {
        return callChatApiWithModelCode(prompt, null);
    }

    /**
     * 调用指定模型
     * @param prompt 用户提示词
     * @param modelCode 模型代码（spring.ai.models.list 的 key，null 时用默认模型）
     * @return 响应内容
     */
    public String callChatApiWithModelCode(String prompt, String modelCode) {
        ChatProvider aiService = (modelCode == null || modelCode.isEmpty())
                ? aiServiceFactory.getDefaultService()
                : aiServiceFactory.getService(modelCode);

        log.info("使用模型: {}", aiService.getModelName());
        return aiService.callChat(prompt);
    }
}
