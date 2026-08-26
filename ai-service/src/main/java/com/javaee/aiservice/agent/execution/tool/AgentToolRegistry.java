package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentPlanStep;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.client.DocumentServiceClient;
import com.javaee.aiservice.rag.KnowledgeQueryService;
import com.javaee.aiservice.service.AIService;
import com.javaee.aiservice.service.FileDeleteService;
import com.javaee.aiservice.service.FileDownloadService;
import com.javaee.aiservice.service.FileVersionService;
import com.javaee.aiservice.service.RecycleBinService;
import com.javaee.aiservice.skills.SkillExecutorService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent Tool 目录与组合根：每个 Agent Tool 是一个自包含模块（schema + 执行 + 默认参数），
 * 这里只做组装，并对外提供按名执行 / 默认参数注入 / 计划归一化。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public AgentToolRegistry(ChatService chatService, AIService aiService,
                             KnowledgeQueryService knowledgeQueryService,
                             FileDownloadService fileDownloadService, FileDeleteService fileDeleteService,
                             FileVersionService fileVersionService, RecycleBinService recycleBinService,
                             DocumentServiceClient documentServiceClient, SkillExecutorService skillExecutorService) {
        register(new DirectAnswerTool(chatService));
        register(new AskUserTool());
        register(new RagAnswerTool(knowledgeQueryService));
        register(new RagSearchTool(knowledgeQueryService));
        register(new TextSummarizeTool(aiService));
        register(new TextAnalyzeTool(aiService));
        register(new KeywordExtractTool(aiService));
        register(new TextCorrectTool(chatService));
        register(new FileDownloadUrlTool(fileDownloadService));
        register(new FileDeleteTool(fileDeleteService));
        register(new FileRestoreTool(fileDeleteService));
        register(new RecycleListTool(recycleBinService));
        register(new FileVersionListTool(fileVersionService));
        register(new FileVersionSwitchTool(fileVersionService));
        register(new HtmlPptGenerateTool(skillExecutorService));
        register(new DocumentReadTool(documentServiceClient));
        register(new DocumentWriteTool());
        register(new TextToFileTool());
    }

    private void register(AgentTool tool) {
        tools.put(tool.definition().getName(), tool);
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    public List<AgentToolDefinition> list() {
        List<AgentToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            definitions.add(tool.definition());
        }
        return definitions;
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public AgentToolResult execute(String toolName, Map<String, Object> params, AgentExecutionRequest request,
                                   Map<String, Object> context) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            return AgentToolResult.error(toolName, "未知工具: " + toolName);
        }
        return tool.execute(params, request, context);
    }

    public void fillDefaultParams(String toolName, Map<String, Object> params, AgentExecutionRequest request,
                                  Map<String, Object> context) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            params.putIfAbsent("question", request.getTask());
            return;
        }
        tool.fillDefaultParams(params, request, context);
    }

    public Set<String> contextOverrideKeys(String toolName) {
        AgentTool tool = tools.get(toolName);
        return tool == null ? Set.of() : tool.contextOverrideKeys();
    }

    /**
     * 计划归一化：校验工具存在性、风险等级、重试策略、默认参数注入。
     */
    public List<AgentPlanStep> normalizePlan(List<AgentPlanStep> plan, AgentExecutionRequest request,
                                             Map<String, Object> context) {
        List<AgentPlanStep> normalized = new ArrayList<>();
        int index = 1;
        for (AgentPlanStep step : plan) {
            if (!contains(step.getToolName())) {
                step.setToolName("direct-answer");
            }
            if (AgentToolSupport.isBlank(step.getId())) {
                step.setId("step-" + index);
            }
            if (AgentToolSupport.isBlank(step.getDescription())) {
                step.setDescription(request.getTask());
            }
            AgentTool tool = get(step.getToolName());
            if (tool != null) {
                AgentToolDefinition definition = tool.definition();
                if (AgentToolSupport.isBlank(step.getRiskLevel()) || "low".equals(step.getRiskLevel())) {
                    step.setRiskLevel(definition.getRiskLevel());
                }
                step.setRequiresApproval(definition.isDestructive());
                if (AgentToolSupport.isBlank(step.getRetryPolicy())) {
                    step.setRetryPolicy(definition.isDestructive() ? "none" : "exponential");
                }
                if (step.getMaxRetries() <= 0) {
                    step.setMaxRetries(definition.isDestructive() ? 0 : 1);
                }
            }
            fillDefaultParams(step.getToolName(), step.getParams(), request, context);
            normalized.add(step);
            index++;
        }
        return normalized;
    }
}
