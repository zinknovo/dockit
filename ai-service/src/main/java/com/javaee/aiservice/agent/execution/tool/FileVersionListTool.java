package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.FileVersionDTO;
import com.javaee.aiservice.service.FileVersionService;

import java.util.Map;
import java.util.Set;

/**
 * 查看文件版本列表。
 */
public class FileVersionListTool implements AgentTool {

    private final FileVersionService fileVersionService;

    public FileVersionListTool(FileVersionService fileVersionService) {
        this.fileVersionService = fileVersionService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("file-version-list", "查看文件版本列表。",
                Map.of("bucketName", "存储桶名称，可选", "objectName", "对象名称，必填"),
                Set.of("objectName"), false, "file", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        FileVersionDTO dto = new FileVersionDTO();
        dto.setBucketName(AgentToolSupport.asString(params.get("bucketName")));
        dto.setObjectName(AgentToolSupport.requireParam(params, "objectName"));
        dto.setVersionId(AgentToolSupport.asString(params.get("versionId")));
        return AgentToolResult.success("file-version-list", "文件版本查询完成",
                AgentToolSupport.toMap(fileVersionService.getVersions(dto)));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("objectName", resolveObjectName(params, context, request.getTask()));
        params.putIfAbsent("bucketName", context.get("bucketName"));
        params.putIfAbsent("requireConfirmation", true);
    }

    @Override
    public Set<String> contextOverrideKeys() {
        return Set.of("objectName", "bucketName");
    }

    private static String resolveObjectName(Map<String, Object> params, Map<String, Object> context, String task) {
        return AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("objectName")),
                AgentToolSupport.asString(context.get("objectName")),
                AgentToolSupport.extractQuotedText(task),
                AgentToolSupport.extractObjectNameFromTask(task));
    }
}
