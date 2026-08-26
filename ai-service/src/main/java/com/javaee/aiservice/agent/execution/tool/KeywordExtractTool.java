package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.KeywordExtractDTO;
import com.javaee.aiservice.service.AIService;

import java.util.Map;
import java.util.Set;

/**
 * 从文本中提取关键词。
 */
public class KeywordExtractTool implements AgentTool {

    private final AIService aiService;

    public KeywordExtractTool(AIService aiService) {
        this.aiService = aiService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("keyword-extract", "从文本中提取关键词。",
                Map.of("content", "待提取文本", "count", "关键词数量，默认8", "model", "可选模型代码"),
                Set.of("content"), false, "text", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        KeywordExtractDTO dto = new KeywordExtractDTO();
        dto.setContent(AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("content")), ""));
        dto.setCount(AgentToolSupport.intValue(params.get("count"), 8));
        return AgentToolResult.success("keyword-extract", "关键词提取完成",
                AgentToolSupport.toMap(aiService.extractKeywords(dto, AgentToolSupport.asString(params.get("model")))));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("content", AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("content")),
                AgentToolSupport.asString(context.get("content")), request.getTask()));
        params.putIfAbsent("count", 8);
        params.putIfAbsent("instruction", request.getTask());
        params.putIfAbsent("model", request.getModel());
    }
}
