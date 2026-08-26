package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.skills.HtmlPptSkill;
import com.javaee.aiservice.skills.SkillExecutorService;

import java.util.Map;
import java.util.Set;

/**
 * 根据大纲生成 HTML PPT（复用技能系统的 HTML PPT Skill）。
 */
public class HtmlPptGenerateTool implements AgentTool {

    private final SkillExecutorService skillExecutorService;

    public HtmlPptGenerateTool(SkillExecutorService skillExecutorService) {
        this.skillExecutorService = skillExecutorService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("html-ppt-generate", "根据大纲生成 HTML PPT。",
                Map.of("outline", "PPT大纲", "theme", "主题，默认tokyo-night", "title", "标题", "model", "可选模型代码"),
                Set.of("outline"), false, "generation", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        Object result = skillExecutorService.executeSkill(
                HtmlPptSkill.NAME,
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("outline")), ""),
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("theme")), "tokyo-night"),
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("title")), "演示文稿"),
                AgentToolSupport.asString(params.get("model"))
        );
        return AgentToolResult.success("html-ppt-generate", "HTML PPT生成完成",
                AgentToolSupport.toMap(result));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("outline", AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("content")),
                AgentToolSupport.asString(context.get("content")), request.getTask()));
        params.putIfAbsent("title", AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(context.get("title")), "演示文稿"));
        params.putIfAbsent("theme", AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(context.get("theme")), "tokyo-night"));
        params.putIfAbsent("model", request.getModel());
    }
}
