package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.rag.KnowledgeQueryService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 检索知识库并调用模型生成最终问答答案（整条链委托 KnowledgeQueryService）。
 */
public class RagAnswerTool implements AgentTool {

    private final KnowledgeQueryService knowledgeQueryService;

    public RagAnswerTool(KnowledgeQueryService knowledgeQueryService) {
        this.knowledgeQueryService = knowledgeQueryService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("rag-answer", "检索知识库并调用模型生成最终问答答案。",
                Map.of("question", "用户问题", "topK", "检索条数，默认3",
                        "rerankStrategy", "重排序策略，默认HYBRID；只影响候选片段排序，不代表业务检索策略"),
                Set.of("question"), false, "rag", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String question = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("question")),
                AgentToolSupport.asString(params.get("query")));
        Map<String, Object> answerData = knowledgeQueryService.ask(question,
                AgentToolSupport.intValue(params.get("topK"), 3),
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("rerankStrategy")),
                        AgentToolSupport.asString(params.get("strategy"))),
                AgentToolSupport.asString(params.get("userId")),
                AgentToolSupport.asString(params.get("knowledgeBaseId")),
                request.getModel());
        Map<String, Object> data = new LinkedHashMap<>(answerData);
        data.remove("context");
        return AgentToolResult.success("rag-answer", "知识库问答完成", data);
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("question", request.getTask());
        params.putIfAbsent("topK", 3);
        params.putIfAbsent("rerankStrategy",
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("strategy")), "HYBRID"));
        params.putIfAbsent("userId", context.get("userId"));
        params.putIfAbsent("knowledgeBaseId", context.getOrDefault("knowledgeBaseId", "default"));
    }
}
