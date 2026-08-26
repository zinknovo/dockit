package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.client.DocumentServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 读取业务文档当前内容，用于扩写、改写、润色前获取原文档。
 */
public class DocumentReadTool implements AgentTool {

    private final DocumentServiceClient documentServiceClient;

    public DocumentReadTool(DocumentServiceClient documentServiceClient) {
        this.documentServiceClient = documentServiceClient;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("document-read", "读取业务文档当前内容。用于在扩写、改写、润色前获取原文档。",
                Map.of("documentId", "业务文档ID，必填"),
                Set.of("documentId"), false, "document", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String documentId = AgentToolSupport.requireParam(params, "documentId");
        Map<String, Object> document = documentServiceClient.getDocument(documentId);
        String content = Objects.requireNonNullElse(
                AgentToolSupport.firstNonBlank(AgentToolSupport.asString(document.get("content")), ""), "");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("documentId", AgentToolSupport.firstNonBlank(AgentToolSupport.asString(document.get("id")), documentId));
        data.put("documentTitle", document.get("title"));
        data.put("documentContent", content);
        data.put("documentVersion", document.get("version"));
        data.put("document", document);
        data.put("contentLength", content.length());
        return AgentToolResult.success("document-read", "业务文档读取完成: " + documentId, data);
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("documentId", AgentToolSupport.asString(context.get("documentId")));
    }
}
