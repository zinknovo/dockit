package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.TextAnalyzeDTO;
import com.javaee.aiservice.service.AIService;

import java.util.Map;
import java.util.Set;

/**
 * 统计文本字符、中文、英文、数字、空格、标点、行数。
 */
public class TextAnalyzeTool implements AgentTool {

    private final AIService aiService;

    public TextAnalyzeTool(AIService aiService) {
        this.aiService = aiService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("text-analyze", "统计文本字符、中文、英文、数字、空格、标点、行数。",
                Map.of("content", "待分析文本"), Set.of("content"), false, "text", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        TextAnalyzeDTO dto = new TextAnalyzeDTO();
        dto.setContent(AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("content")), ""));
        return AgentToolResult.success("text-analyze", "文本分析完成",
                AgentToolSupport.toMap(aiService.analyze(dto)));
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
