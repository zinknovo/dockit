package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.FileDeleteDTO;
import com.javaee.aiservice.service.FileDeleteService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 永久删除指定文件，不进入回收站。可传前端 documentId（删除对应业务文档），
 * 或兼容直接传 objectName 永久删除 MinIO 对象。删除必须由前端先确认。
 */
public class FileDeleteTool implements AgentTool {

    private final FileDeleteService fileDeleteService;

    public FileDeleteTool(FileDeleteService fileDeleteService) {
        this.fileDeleteService = fileDeleteService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("file-delete", "永久删除指定文件，不进入回收站。可传前端documentId，由服务端删除对应业务文档；也兼容直接传objectName永久删除MinIO对象。",
                Map.of("bucketName", "存储桶名称，可选",
                        "objectName", "对象名称，与documentId二选一",
                        "documentId", "前端业务文档ID，与objectName二选一"),
                Set.of(), false, "file", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        FileDeleteDTO dto = new FileDeleteDTO();
        dto.setBucketName(AgentToolSupport.asString(params.get("bucketName")));
        dto.setObjectName(AgentToolSupport.asString(params.get("objectName")));
        dto.setDocumentId(AgentToolSupport.asString(params.get("documentId")));
        if (AgentToolSupport.isBlank(dto.getObjectName()) && AgentToolSupport.isBlank(dto.getDocumentId())) {
            return AgentToolResult.actionRequired("file-delete", "请提供前端documentId或MinIO对象名称objectName",
                    Map.of("missingParameters", List.of("documentId/objectName")));
        }
        dto.setRequireConfirmation(false);
        dto.setConfirmationToken(null);
        String deleter = "agent-approved:" + AgentToolSupport.valueOrDefault(request.getUserId(), "agent");
        return AgentToolResult.success("file-delete", "删除请求处理完成",
                AgentToolSupport.toMap(fileDeleteService.deleteFile(dto, deleter)));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("documentId", AgentToolSupport.asString(context.get("documentId")));
        params.putIfAbsent("objectName", resolveObjectName(params, context, request.getTask()));
        params.putIfAbsent("bucketName", context.get("bucketName"));
        params.put("requireConfirmation", false);
    }

    @Override
    public Set<String> contextOverrideKeys() {
        return Set.of("objectName", "bucketName", "documentId");
    }

    private static String resolveObjectName(Map<String, Object> params, Map<String, Object> context, String task) {
        return AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("objectName")),
                AgentToolSupport.asString(context.get("objectName")),
                AgentToolSupport.extractQuotedText(task),
                AgentToolSupport.extractObjectNameFromTask(task));
    }
}
