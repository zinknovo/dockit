package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务参数缺失或存在歧义时，向用户提出澄清问题并挂起任务等待补充。
 */
public class AskUserTool implements AgentTool {

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("ask-user", "当任务参数缺失或存在歧义时，向用户提出澄清问题。不要在已经掌握所需信息时使用。",
                Map.of("question", "需要向用户澄清的问题",
                        "missingFields", "本次缺失的字段名，逗号分隔，可选",
                        "options", "可供用户选择的候选项，分号分隔，可选"),
                Set.of("question"), false, "interaction", true);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String question = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("question")), "请补充更多信息以便继续。");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("question", question);
        String missing = AgentToolSupport.asString(params.get("missingFields"));
        if (!AgentToolSupport.isBlank(missing)) {
            data.put("missingFields", List.of(missing.split("[,，;；]")));
        }
        String options = AgentToolSupport.asString(params.get("options"));
        if (!AgentToolSupport.isBlank(options)) {
            data.put("options", List.of(options.split("[;；]")));
        }
        data.put("interactionType", "user_input");
        data.put("resumeMode", "continue_trace");
        data.put("resumeEndpointTemplate", "/api/ai/agent/tasks/{traceId}/continue");
        return AgentToolResult.actionRequired("ask-user", question, data);
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("question", request.getTask());
    }
}
