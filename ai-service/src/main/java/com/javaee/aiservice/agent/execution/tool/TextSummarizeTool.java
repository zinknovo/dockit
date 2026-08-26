package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.TextSummarizeDTO;
import com.javaee.aiservice.service.AIService;

import java.util.Map;
import java.util.Set;

/**
 * 对文本进行摘要。
 */
public class TextSummarizeTool implements AgentTool {

    private final AIService aiService;

    public TextSummarizeTool(AIService aiService) {
        this.aiService = aiService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("text-summarize", "对文本进行摘要。",
                Map.of("content", "待摘要文本", "maxLength", "摘要最大长度，默认300", "model", "可选模型代码"),
                Set.of("content"), false, "text", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        TextSummarizeDTO dto = new TextSummarizeDTO();
        dto.setContent(AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("content")), ""));
        dto.setMaxLength(AgentToolSupport.intValue(params.get("maxLength"), 300));
        return AgentToolResult.success("text-summarize", "摘要完成",
                AgentToolSupport.toMap(aiService.summarize(dto, AgentToolSupport.asString(params.get("model")))));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("content", AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("content")),
                AgentToolSupport.asString(context.get("content")), request.getTask()));
        params.putIfAbsent("maxLength", 300);
        params.putIfAbsent("model", request.getModel());
    }
}
