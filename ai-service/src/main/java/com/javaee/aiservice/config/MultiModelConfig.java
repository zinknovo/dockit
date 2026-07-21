package com.javaee.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "ai.models")
public class MultiModelConfig {
    
    private Map<String, ModelConfig> dashscope = new HashMap<>();
    private Map<String, ModelConfig> openai = new HashMap<>();
    
    public Map<String, ModelConfig> getDashscope() {
        return dashscope;
    }
    
    public void setDashscope(Map<String, ModelConfig> dashscope) {
        this.dashscope = dashscope;
    }
    
    public Map<String, ModelConfig> getOpenai() {
        return openai;
    }
    
    public void setOpenai(Map<String, ModelConfig> openai) {
        this.openai = openai;
    }
    
    public static class ModelConfig {
        private String apiKey;
        private String model;
        private String baseUrl;
        /**
         * 实际调用 provider API 时使用的模型名。
         * 为空时回退到 model（再为空时回退到 ModelType 的 code）。
         * 用于把内部模型标识映射到不同厂商的实际模型名，例如 qwen3.6-plus -> deepseek-v4-pro。
         */
        private String apiModelName;
        private boolean enabled = true;
        
        public String getApiKey() {
            return apiKey;
        }
        
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
        
        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiModelName() {
            return apiModelName;
        }

        public void setApiModelName(String apiModelName) {
            this.apiModelName = apiModelName;
        }

        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
