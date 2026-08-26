package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.dto.FileRestoreDTO;
import com.javaee.aiservice.service.FileDeleteService;

import java.util.Map;
import java.util.Set;

/**
 * 从回收站恢复文件。危险操作，需人工审批。
 */
public class FileRestoreTool implements AgentTool {

    private final FileDeleteService fileDeleteService;

    public FileRestoreTool(FileDeleteService fileDeleteService) {
        this.fileDeleteService = fileDeleteService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("file-restore", "从回收站恢复文件。",
                Map.of("recycleId", "回收站记录ID", "bucketName", "存储桶名称，可选", "newObjectName", "新对象名称，可选"),
                Set.of("recycleId"), true, "file", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        FileRestoreDTO dto = new FileRestoreDTO();
        dto.setRecycleId(AgentToolSupport.requireParam(params, "recycleId"));
        dto.setBucketName(AgentToolSupport.asString(params.get("bucketName")));
        dto.setNewObjectName(AgentToolSupport.asString(params.get("newObjectName")));
        return AgentToolResult.success("file-restore", "文件恢复完成",
                AgentToolSupport.toMap(fileDeleteService.restoreFile(dto)));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("recycleId", context.get("recycleId"));
        params.putIfAbsent("bucketName", context.get("bucketName"));
    }

    @Override
    public Set<String> contextOverrideKeys() {
        return Set.of("recycleId", "bucketName", "newObjectName");
    }
}
