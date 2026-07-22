package com.javaee.aiservice.agent;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 提示词工程服务
 * 使用Spring AI内置工具实现智能提示词拼接
 * 支持多种提示词模板的管理和动态生成
 */
@Service
public class PromptEngineeringService {

    /**
     * 总结任务提示词模板
     */
    private static final String SUMMARIZE_TEMPLATE = """
            请对以下文本进行总结：

            {content}

            要求：
            1. 总结内容不超过{maxLength}字
            2. 保留核心要点
            3. 语言简洁明了
            """;

    /**
     * 关键词提取提示词模板
     */
    private static final String KEYWORD_EXTRACT_TEMPLATE = """
            请从以下文本中提取关键词：

            {content}

            要求：
            1. 提取{count}个核心关键词
            2. 用中文逗号分隔
            3. 按重要性排序
            """;

    private static final String RAG_ANSWER_TEMPLATE = """
            你是DocAI知识库问答助手。请严格基于【知识库片段】回答问题。
            如果片段中没有答案，请回答"知识库中未找到相关信息"，不要编造。

            【知识库片段】
            {knowledgeContext}

            【问题】
            {question}

            请给出结构化、可执行的答案，并在必要时引用来源编号。
            """;

    /**
     * 生成总结提示词
     * @param content 待总结内容
     * @param maxLength 最大长度
     * @return 提示词字符串
     */
    public String createSummarizePrompt(String content, int maxLength) {
        PromptTemplate template = new PromptTemplate(SUMMARIZE_TEMPLATE);
        Map<String, Object> params = new HashMap<>();
        params.put("content", content);
        params.put("maxLength", maxLength);
        return template.render(params);
    }

    /**
     * 生成关键词提取提示词
     * @param content 待分析内容
     * @param count 关键词数量
     * @return 提示词字符串
     */
    public String createKeywordExtractPrompt(String content, int count) {
        PromptTemplate template = new PromptTemplate(KEYWORD_EXTRACT_TEMPLATE);
        Map<String, Object> params = new HashMap<>();
        params.put("content", content);
        params.put("count", count);
        return template.render(params);
    }

    public String createRagAnswerPrompt(String question, String knowledgeContext) {
        PromptTemplate template = new PromptTemplate(RAG_ANSWER_TEMPLATE);
        Map<String, Object> params = new HashMap<>();
        params.put("question", question);
        params.put("knowledgeContext", knowledgeContext != null ? knowledgeContext : "");
        return template.render(params);
    }
}
