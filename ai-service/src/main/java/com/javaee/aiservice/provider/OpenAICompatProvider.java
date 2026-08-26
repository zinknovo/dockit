package com.javaee.aiservice.provider;

import com.javaee.aiservice.agent.ChatProvider;
import com.javaee.aiservice.agent.OpenAIAIService;
import com.javaee.aiservice.config.MultiModelConfig;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容协议 provider：适用于 DeepSeek / DashScope 兼容端点 / 火山方舟 等一切提供
 * /chat/completions 的厂商，协议差异通过 base-url + api-model-name 配置化消除。
 */
@Component
public class OpenAICompatProvider implements AIServiceProvider {

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public ChatProvider create(String modelCode, MultiModelConfig.ModelConfig config) {
        if (config == null || config.getApiKey() == null || config.getBaseUrl() == null) {
            return null;
        }
        String modelName = config.getName() != null && !config.getName().isEmpty() ? config.getName() : modelCode;
        return new OpenAIAIService(config.getApiKey(), config.getBaseUrl(),
                modelCode, modelName, config.getApiModelName(), config.isEnabled());
    }
}
