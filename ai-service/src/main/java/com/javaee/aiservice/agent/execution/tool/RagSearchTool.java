package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.rag.KnowledgeQueryService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 只检索知识库，返回相关片段和来源，不生成答案（检索委托 KnowledgeQueryService）。
 */
public class RagSearchTool implements AgentTool {

    private final KnowledgeQueryService knowledgeQueryService;

    public RagSearchTool(KnowledgeQueryService knowledgeQueryService) {
        this.knowledgeQueryService = knowledgeQueryService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("rag-search", "只检索知识库，返回相关片段和来源，不生成答案。",
                Map.of("query", "查询词", "topK", "检索条数，默认5",
                        "rerankStrategy", "重排序策略，默认HYBRID；只影响候选片段排序，不代表业务检索策略"),
                Set.of("query"), false, "rag", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String query = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("query")),
                AgentToolSupport.asString(params.get("question")));
        List<Map<String, Object>> results = knowledgeQueryService.search(query,
                AgentToolSupport.intValue(params.get("topK"), 5),
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("rerankStrategy")),
                        AgentToolSupport.asString(params.get("strategy"))),
                AgentToolSupport.asString(params.get("userId")),
                AgentToolSupport.asString(params.get("knowledgeBaseId")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("results", results);
        data.put("sources", results.stream().map(r -> r.get("id")).toList());
        return AgentToolResult.success("rag-search", "知识库检索完成", data);
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("query", request.getTask());
        params.putIfAbsent("topK", 5);
        params.putIfAbsent("rerankStrategy",
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("strategy")), "HYBRID"));
        params.putIfAbsent("userId", context.get("userId"));
        params.putIfAbsent("knowledgeBaseId", context.getOrDefault("knowledgeBaseId", "default"));
    }
}
