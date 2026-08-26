package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.FileVersionSwitchDTO;
import com.javaee.aiservice.service.FileVersionService;

import java.util.Map;
import java.util.Set;

/**
 * 切换文件当前版本。危险操作，需人工审批。
 */
public class FileVersionSwitchTool implements AgentTool {

    private final FileVersionService fileVersionService;

    public FileVersionSwitchTool(FileVersionService fileVersionService) {
        this.fileVersionService = fileVersionService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("file-version-switch", "切换文件当前版本。",
                Map.of("bucketName", "存储桶名称，可选", "objectName", "对象名称，必填",
                        "targetVersionId", "目标版本ID"),
                Set.of("objectName", "targetVersionId"), true, "file", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        FileVersionSwitchDTO dto = new FileVersionSwitchDTO();
        dto.setBucketName(AgentToolSupport.asString(params.get("bucketName")));
        dto.setObjectName(AgentToolSupport.requireParam(params, "objectName"));
        dto.setTargetVersionId(AgentToolSupport.requireParam(params, "targetVersionId"));
        return AgentToolResult.success("file-version-switch", "文件版本切换完成",
                AgentToolSupport.toMap(fileVersionService.switchVersion(dto)));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("objectName", resolveObjectName(params, context, request.getTask()));
        params.putIfAbsent("bucketName", context.get("bucketName"));
        params.putIfAbsent("requireConfirmation", true);
    }

    @Override
    public Set<String> contextOverrideKeys() {
        return Set.of("objectName", "bucketName", "targetVersionId");
    }

    private static String resolveObjectName(Map<String, Object> params, Map<String, Object> context, String task) {
        return AgentToolSupport.firstNonBlank(
                AgentToolSupport.asString(params.get("objectName")),
                AgentToolSupport.asString(context.get("objectName")),
                AgentToolSupport.extractQuotedText(task),
                AgentToolSupport.extractObjectNameFromTask(task));
    }
}
