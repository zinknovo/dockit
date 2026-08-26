package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;

import java.util.Map;
import java.util.Set;

/**
 * 对文本进行纠错、润色或改写。
 */
public class TextCorrectTool implements AgentTool {

    private final ChatService chatService;

    public TextCorrectTool(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("text-correct", "对文本进行纠错、润色或改写。",
                Map.of("content", "待处理文本", "instruction", "纠错/润色/改写要求", "model", "可选模型代码"),
                Set.of("content"), false, "text", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String content = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("content")), "");
        String instruction = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("instruction")),
                "请对文本进行纠错和润色");
        String prompt = """
                %s

                文本:
                %s

                请返回修改后的文本、问题说明和修改建议。
                """.formatted(instruction, content);
        String result = chatService.callChatApiWithModelCode(prompt, AgentToolSupport.asString(params.get("model")));
        return AgentToolResult.success("text-correct", "文本处理完成", Map.of("result", result));
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
