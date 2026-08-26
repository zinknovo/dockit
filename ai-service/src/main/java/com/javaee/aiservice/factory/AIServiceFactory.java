package com.javaee.aiservice.factory;

import com.javaee.aiservice.agent.ChatProvider;
import com.javaee.aiservice.config.MultiModelConfig;
import com.javaee.aiservice.provider.AIServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 服务工厂：按配置 spring.ai.models.list 构建模型服务。
 * 每个模型条目声明 provider 名称，从 provider 注册表取适配器创建服务；
 * 未在配置中声明的模型不会注册（模型清单完全由配置驱动，加模型只需加配置）。
 */
@Component
public class AIServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(AIServiceFactory.class);

    private final MultiModelConfig multiModelConfig;

    private final Map<String, AIServiceProvider> providerMap;

    private final Map<String, ChatProvider> serviceMap = new ConcurrentHashMap<>();

    @Autowired
    public AIServiceFactory(MultiModelConfig multiModelConfig, List<AIServiceProvider> providers) {
        this.multiModelConfig = multiModelConfig;
        Map<String, AIServiceProvider> map = new HashMap<>();
        for (AIServiceProvider provider : providers) {
            map.put(provider.name(), provider);
        }
        this.providerMap = map;
    }

    @PostConstruct
    public void init() {
        log.info("初始化AI服务工厂，provider 注册表: {}", providerMap.keySet());

        for (Map.Entry<String, MultiModelConfig.ModelConfig> entry : multiModelConfig.getList().entrySet()) {
            String code = entry.getKey();
            MultiModelConfig.ModelConfig config = entry.getValue();

            AIServiceProvider provider = providerMap.get(config.getProvider());
            if (provider == null) {
                log.warn("模型 {} 指定了未注册的 provider '{}'，跳过注册", code, config.getProvider());
                continue;
            }

            ChatProvider service = provider.create(code, config);
            if (service == null) {
                log.warn("模型 {} 缺少 apiKey 或 baseUrl 配置，跳过注册", code);
                continue;
            }

            serviceMap.put(code, service);
            log.info("注册模型: {} via provider '{}' (enabled: {})", code, config.getProvider(), config.isEnabled());
        }

        log.info("AI服务工厂初始化完成，共注册 {} 个模型", serviceMap.size());
    }

    public ChatProvider getService(String modelCode) {
        ChatProvider service = serviceMap.get(modelCode);
        if (service == null) {
            throw new IllegalArgumentException("不支持的模型类型: " + modelCode + "（未在配置中声明）");
        }
        if (!service.isAvailable()) {
            throw new IllegalStateException("模型 " + service.getModelName() + " 已禁用");
        }
        return service;
    }

    public ChatProvider getDefaultService() {
        return getService(multiModelConfig.getDefaultModel());
    }

    public Map<String, ChatProvider> getAllServices() {
        return new HashMap<>(serviceMap);
    }
}
