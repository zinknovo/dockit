package com.javaee.aiservice.provider;

import com.javaee.aiservice.agent.ChatProvider;
import com.javaee.aiservice.config.MultiModelConfig;

/**
 * 模型服务 provider 适配器：按模型配置创建对应的 ChatProvider 实现。
 * 新增协议（如 anthropic）时实现本接口并注册为 Spring Bean，配置里把模型指到新 provider 即可，无需改工厂。
 */
public interface AIServiceProvider {

    /** provider 名称，与配置中模型的 provider 字段对应 */
    String name();

    /** 按模型配置创建服务实例；配置缺失时返回 null 表示该模型不可用 */
    ChatProvider create(String modelCode, MultiModelConfig.ModelConfig config);
}
