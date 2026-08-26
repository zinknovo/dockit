package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;

import java.util.Map;
import java.util.Set;

/**
 * 不调用外部工具，直接基于模型和上下文回答用户。
 */
public class DirectAnswerTool implements AgentTool {

    private final ChatService chatService;

    public DirectAnswerTool(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("direct-answer", "不调用外部工具，直接基于模型和上下文回答用户。",
                Map.of("question", "用户问题"), Set.of("question"), false, "llm", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String question = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("question")), request.getTask());
        String answer = chatService.callChatApiWithModelCode(question, request.getModel());
        return AgentToolResult.success("direct-answer", "直接回答完成", Map.of("answer", answer));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("question", request.getTask());
    }
}
