package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;

import java.util.Map;
import java.util.Set;

/**
 * 兼容旧写文件工具名：将 AI 生成文本返回给前端编辑器待写入，不直接调用 MinIO。
 */
public class TextToFileTool extends AbstractFrontendWriteTool {

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("text-to-file", "兼容旧的写文件工具名：将AI生成文本返回给前端编辑器待写入，不直接调用MinIO。",
                Map.of("content", "要写入的文本内容，必填",
                        "objectName", "旧流程的文件对象名称，可选，仅用于兼容展示",
                        "bucketName", "旧流程的存储桶名称，可选，仅用于兼容展示",
                        "contentType", "内容类型，默认text/plain",
                        "writeMode", "写入模式: append(追加,默认) / overwrite(覆盖) / replace-selection(替换选中内容) / insert(插入)",
                        "changeLog", "本次前端增量修改说明，默认由Agent生成",
                        "insertAfterText", "从当前文档中复制的一小段锚点文本，前端会把内容插入到该段后面，可选",
                        "selectionText", "前端当前选中的文本，可选"),
                Set.of("content"), false, "file", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        String content = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("content")),
                AgentToolSupport.asString(context.get("answer")), AgentToolSupport.asString(context.get("lastAnswer")));
        if (AgentToolSupport.isBlank(content)) {
            return AgentToolResult.error("text-to-file", "缺少要写入的文本内容，请先执行 direct-answer 生成内容");
        }

        String objectName = AgentToolSupport.asString(params.get("objectName"));
        String bucketName = AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("bucketName")),
                AgentToolSupport.asString(context.get("bucketName")),
                AgentToolSupport.defaultUserBucketName(request, context));
        String contentType = AgentToolSupport.firstNonBlank(AgentToolSupport.asString(params.get("contentType")), "text/plain");
        Map<String, Object> writePayload = parseFrontendWritePayload(content);
        String payloadContent = AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(writePayload.get("content")), content);

        return buildFrontendWriteResult(
                "text-to-file",
                payloadContent,
                normalizeFrontendWriteMode(AgentToolSupport.firstNonBlank(
                        AgentToolSupport.asString(writePayload.get("writeMode")), AgentToolSupport.asString(params.get("writeMode")))),
                AgentToolSupport.firstNonBlank(
                        AgentToolSupport.asString(params.get("documentId")), AgentToolSupport.asString(context.get("documentId"))),
                bucketName,
                objectName,
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
                contentType
        );
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        fillGeneratedContent(params, context, request.getTask());
        params.putIfAbsent("objectName", resolveObjectName(params, context, request.getTask()));
        params.putIfAbsent("bucketName", context.get("bucketName"));
        params.putIfAbsent("contentType", "text/plain");
        params.putIfAbsent("writeMode", AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(context.get("writeMode")), "append"));
        params.putIfAbsent("changeLog", "AI Agent生成前端文档增量内容");
        params.putIfAbsent("selectionText", AgentToolSupport.asString(context.get("selectedText")));
        params.putIfAbsent("insertAfterText", AgentToolSupport.asString(context.get("insertAfterText")));
    }
}
