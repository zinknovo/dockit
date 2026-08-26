package com.javaee.aiservice.agent.execution.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaee.aiservice.agent.execution.GeneratedContentSanitizer;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * document-write / text-to-file 共用：把生成内容组装为"前端写入补丁"结果，
 * 不持久化到 MinIO，由前端确认后自行保存。
 */
abstract class AbstractFrontendWriteTool implements AgentTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected AgentToolResult buildFrontendWriteResult(String toolName,
                                                       String content,
                                                       String writeMode,
                                                       String documentId,
                                                       String bucketName,
                                                       String objectName,
                                                       String changeLog,
                                                       String selectionText,
                                                       String insertAfterText,
                                                       String contentFormat) {
        String normalizedContentFormat = AgentToolSupport.firstNonBlank(contentFormat, "plain_text");
        String finalContent = "html".equalsIgnoreCase(normalizedContentFormat)
                ? content
                : GeneratedContentSanitizer.sanitize(content);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", "frontend-document-write");
        data.put("frontendAction", "apply-document-content");
        data.put("requiresFrontendWrite", true);
        data.put("persisted", false);
        data.put("documentId", AgentToolSupport.firstNonBlank(documentId, "current-editor"));
        data.put("writeMode", writeMode);
        data.put("content", finalContent);
        data.put("contentFormat", normalizedContentFormat);
        data.put("changeLog", AgentToolSupport.firstNonBlank(changeLog, "AI Agent生成前端文档增量内容"));
        data.put("selectionText", selectionText);
        data.put("insertAfterText", GeneratedContentSanitizer.sanitizeAnchor(insertAfterText));
        data.put("contentLength", finalContent.getBytes(StandardCharsets.UTF_8).length);
        data.put("nextStep", "前端已收到待写入内容；用户确认后可使用现有保存功能持久化到MinIO。");
        if (!AgentToolSupport.isBlank(bucketName)) {
            data.put("bucketName", bucketName);
        }
        if (!AgentToolSupport.isBlank(objectName)) {
            data.put("objectName", objectName);
        }

        String target = !AgentToolSupport.isBlank(documentId)
                ? documentId
                : AgentToolSupport.firstNonBlank(objectName, "current-editor");
        String message = "%s已生成前端写入内容: %s (%s)，尚未保存到MinIO".formatted(toolName, target, writeMode);
        return AgentToolResult.success(toolName, message, data);
    }

    protected Map<String, Object> parseFrontendWritePayload(String rawContent) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("content", rawContent);
        if (AgentToolSupport.isBlank(rawContent)) {
            return fallback;
        }

        String text = rawContent.trim();
        if (!text.startsWith("{") && !text.startsWith("```")) {
            return fallback;
        }

        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(stripObjectJson(text), new TypeReference<>() {
            });
            if (parsed.containsKey("content")) {
                return parsed;
            }
        } catch (Exception e) {
            // 前端写入结构化JSON解析失败，按普通正文处理
        }
        return fallback;
    }

    protected String normalizeFrontendWriteMode(String writeMode) {
        String mode = AgentToolSupport.firstNonBlank(writeMode, "append").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "overwrite", "append", "insert", "replace-selection" -> mode;
            default -> "append";
        };
    }

    protected String resolveGeneratedContent(Map<String, Object> params, Map<String, Object> context) {
        return AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("content")),
                AgentToolSupport.asString(context == null ? null : context.get("answer")),
                AgentToolSupport.asString(context == null ? null : context.get("lastAnswer")),
                AgentToolSupport.asString(context == null ? null : context.get("content"))
        );
    }

    protected String resolveObjectName(Map<String, Object> params, Map<String, Object> context, String task) {
        return AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("objectName")),
                AgentToolSupport.asString(context == null ? null : context.get("objectName")),
                AgentToolSupport.extractQuotedText(task),
                AgentToolSupport.extractObjectNameFromTask(task));
    }

    protected void fillGeneratedContent(Map<String, Object> params, Map<String, Object> context, String task) {
        String generatedContent = resolveGeneratedContent(params, context);
        Object existingContent = params.get("content");
        if (!(existingContent instanceof String s && s.startsWith("${")) && !AgentToolSupport.isBlank(generatedContent)) {
            params.putIfAbsent("content", generatedContent);
        }
    }

    private static String stripObjectJson(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
