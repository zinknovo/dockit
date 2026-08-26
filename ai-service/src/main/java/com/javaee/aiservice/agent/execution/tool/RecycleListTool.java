package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.service.RecycleBinService;

import java.util.Map;
import java.util.Set;

/**
 * 查看回收站文件列表。
 */
public class RecycleListTool implements AgentTool {

    private final RecycleBinService recycleBinService;

    public RecycleListTool(RecycleBinService recycleBinService) {
        this.recycleBinService = recycleBinService;
    }

    @Override
    public AgentToolDefinition definition() {
        return ToolDefinitions.create("recycle-list", "查看回收站文件列表。",
                Map.of("bucketName", "存储桶名称，可选"), Set.of(), false, "file", false);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        return AgentToolResult.success("recycle-list", "回收站查询完成",
                AgentToolSupport.toMap(recycleBinService.listRecycleBin(
                        AgentToolSupport.asString(params.get("bucketName")), request.getUserId())));
    }

    @Override
    public void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context) {
        params.putIfAbsent("question", request.getTask());
    }
}
