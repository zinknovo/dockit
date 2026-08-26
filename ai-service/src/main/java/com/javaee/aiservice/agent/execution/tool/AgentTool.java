package com.javaee.aiservice.agent.execution.tool;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;

import java.util.Map;
import java.util.Set;

/**
 * Agent 执行计划中可调用的原子能力模块：schema（definition）与执行（execute）同处一地。
 * 默认参数注入与审批上下文覆盖键由各工具自行声明，注册表只做组装。
 */
public interface AgentTool {

    AgentToolDefinition definition();

    AgentToolResult execute(Map<String, Object> params, AgentExecutionRequest request, Map<String, Object> context);

    /**
     * 为步骤参数注入该工具的默认值；仅注入缺失项。
     */
    default void fillDefaultParams(Map<String, Object> params, AgentExecutionRequest request,
                                   Map<String, Object> context) {
    }

    /**
     * 审批恢复时允许从 context 覆盖的步骤参数键（危险操作参数以审批时为准）。
     */
    default Set<String> contextOverrideKeys() {
        return Set.of();
    }
}
