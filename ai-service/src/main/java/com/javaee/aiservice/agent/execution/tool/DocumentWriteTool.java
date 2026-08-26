package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;

import java.util.Map;
import java.util.Set;

/**
 * 将 AI 生成的文本内容返回给前端，由当前编辑器直接写入页面文档；
 * 不在服务端保存文件或刷新 MinIO 对象。
 */
public class DocumentWriteTool extends AbstractFrontendWriteTool {

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("document-write", "将AI生成的文本内容返回给前端，由当前编辑器直接写入页面文档；不在服务端保存文件或刷新MinIO对象。",
                Map.of("documentId", "业务文档ID，可选，用于前端校验当前文档",
                        "content", "要写回文档的完整文本内容，必填",
                        "writeMode", "写入模式: append(追加,默认) / overwrite(覆盖) / replace-selection(替换选中内容) / insert(插入)",
                        "changeLog", "本次前端增量修改说明，默认由Agent生成",
                        "selectionText", "前端当前选中的文本，可选",
                        "insertAfterText", "从当前文档中复制的一小段锚点文本，前端会把内容插入到该段后面，可选",
                        "contentFormat", "内容格式: plain_text(默认) / html"),
                Set.of("content"), false, "document", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String documentId = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("documentId")),
                AgentToolSupport.asString(context.get("documentId")));
        String content = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("content")),
                AgentToolSupport.asString(context.get("answer")), AgentToolSupport.asString(context.get("lastAnswer")));
        if (AgentToolSupport.isBlank(content)) {
            return AgentToolResult.error("document-write", "缺少要写回文档的文本内容，请先执行 direct-answer 生成内容");
        }

        Map<String, Object> writePayload = parseFrontendWritePayload(content);
        String payloadContent = AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(writePayload.get("content")), content);
        String contentFormat = AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(writePayload.get("contentFormat")),
                AgentToolSupport.asString(params.get("contentFormat")), "plain_text");

        return buildFrontendWriteResult(
                "document-write",
                payloadContent,
                normalizeFrontendWriteMode(AgentToolSupport.firstNonBlank(
                        AgentToolSupport.asString(writePayload.get("writeMode")), AgentToolSupport.asString(params.get("writeMode")))),
                documentId,
                null,
                null,
                AgentToolSupport.firstNonBlank(
                        AgentToolSupport.asString(writePayload.get("changeLog")),
                        AgentToolSupport.asString(params.get("changeLog")), "AI Agent生成前端文档增量内容"),
                AgentToolSupport.firstNonBlank(
                        AgentToolSupport.asString(params.get("selectionText")), AgentToolSupport.asString(context.get("selectedText"))),
                AgentToolSupport.firstNonBlank(
                        AgentToolSupport.asString(writePayload.get("insertAfterText")),
                        AgentToolSupport.asString(writePayload.get("anchorText")),
                        AgentToolSupport.asString(writePayload.get("insertionAnchor")),
                        AgentToolSupport.asString(params.get("insertAfterText")),
                        AgentToolSupport.asString(context.get("insertAfterText"))),
                contentFormat
        );
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        fillGeneratedContent(params, context, request.getTask());
        params.putIfAbsent("documentId", AgentToolSupport.asString(context.get("documentId")));
        params.putIfAbsent("writeMode", AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(context.get("writeMode")), "append"));
        params.putIfAbsent("changeLog", "AI Agent生成前端文档增量内容");
        params.putIfAbsent("knowledgeBaseId",
                AgentToolSupport.valueOrDefault(request.getKnowledgeBaseId(), "default"));
        params.putIfAbsent("selectionText", AgentToolSupport.asString(context.get("selectedText")));
        params.putIfAbsent("insertAfterText", AgentToolSupport.asString(context.get("insertAfterText")));
        params.putIfAbsent("contentFormat", "plain_text");
    }
}
