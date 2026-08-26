package com.javaee.aiservice.internal;

import com.javaee.aiservice.agent.execution.tool.AgentToolRegistry;
import com.javaee.aiservice.security.RequestUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 内部服务代理层
 * 实现权限验证、操作审批、审计记录等功能
 * 作为AI Agent与外部系统交互的安全层
 */
@Component
public class InternalService {

    private static final Logger log = LoggerFactory.getLogger(InternalService.class);

    private final RequestUserContext requestUserContext;
    private final AgentToolRegistry toolRegistry;

    @Autowired
    public InternalService(RequestUserContext requestUserContext, AgentToolRegistry toolRegistry) {
        this.requestUserContext = requestUserContext;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 检查权限。破坏性操作的判定以 AgentToolRegistry 的声明为单一事实源，
     * 执行时还需 context 中带服务端确认标记 confirmedAction。
     * @param skillName 技能名称
     * @param context 上下文信息
     * @return 是否有权限
     */
    public boolean hasPermission(String skillName, Map<String, Object> context) {
        log.debug("检查权限: skillName={}", skillName);

        String contextUserId = String.valueOf(context.getOrDefault("userId", ""));
        String authenticatedUserId = requestUserContext.getRequiredUserId();
        String role = requestUserContext.getCurrentRole();

        if (contextUserId.isBlank() || "default".equals(contextUserId) || "anonymous".equals(contextUserId)) {
            log.warn("权限检查失败，缺少有效用户上下文: skillName={}", skillName);
            return false;
        }

        if (!requestUserContext.isAdmin() && !authenticatedUserId.equals(contextUserId)) {
            log.warn("权限检查失败，用户上下文不匹配: authenticated={}, context={}, skillName={}",
                    authenticatedUserId, contextUserId, skillName);
            return false;
        }

        var tool = toolRegistry.get(skillName);
        boolean destructive = tool != null && tool.definition().isDestructive();
        if (destructive && !Boolean.TRUE.equals(context.get("confirmedAction"))) {
            log.warn("权限检查失败，危险操作缺少服务端确认: userId={}, skillName={}", contextUserId, skillName);
            return false;
        }

        log.info("用户权限检查通过: userId={}, role={}, skillName={}", contextUserId, role, skillName);
        return true;
    }

    /**
     * 记录审计日志
     * @param skillName 技能名称
     * @param params 参数
     * @param result 结果
     */
    public void logAudit(String skillName, Map<String, Object> params, Map<String, Object> result) {
        log.info("审计记录: skillName={}, params={}, result={}", skillName, params, result);
    }

    /**
     * 发送告警
     * @param alertData 告警数据
     */
    public void sendAlert(Map<String, Object> alertData) {
        log.warn("发送告警: {}", alertData);
    }
}
