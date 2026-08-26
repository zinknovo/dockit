package com.javaee.aiservice.agent;

/**
 * 模型调用者接口：一个模型提供方（如 OpenAI 兼容端点）的适配。
 * 曾名 AIService，与 service.AIService（文本能力门面）撞名，更名为 ChatProvider 消除两义。
 */
public interface ChatProvider {

    String callChat(String prompt);

    /** 模型显示名（配置 name，回退到 code） */
    String getModelName();

    /**
     * 实际使用的 provider 名称（如 openai / anthropic），由创建时传入
     */
    String getProviderName();

    boolean isAvailable();
}
