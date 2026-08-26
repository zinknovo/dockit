package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.FileDownloadDTO;
import com.javaee.aiservice.service.FileDownloadService;

import java.util.Map;
import java.util.Set;

/**
 * 生成文件预签名下载地址。
 */
public class FileDownloadUrlTool implements AgentTool {

    private final FileDownloadService fileDownloadService;

    public FileDownloadUrlTool(FileDownloadService fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("file-download-url", "生成文件预签名下载地址。",
                Map.of("bucketName", "存储桶名称，可选", "objectName", "对象名称，必填"),
                Set.of("objectName"), false, "file", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        FileDownloadDTO dto = new FileDownloadDTO();
        dto.setBucketName(AgentToolSupport.asString(params.get("bucketName")));
        dto.setObjectName(AgentToolSupport.requireParam(params, "objectName"));
        dto.setDirectDownload(false);
        return AgentToolResult.success("file-download-url", "下载地址生成完成",
                AgentToolSupport.toMap(fileDownloadService.getFileUrl(dto)));
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
