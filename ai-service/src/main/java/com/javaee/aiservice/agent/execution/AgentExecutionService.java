package com.javaee.aiservice.agent.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaee.aiservice.agent.ChatService;
import com.javaee.aiservice.agent.execution.approval.AgentApprovalService;
import com.javaee.aiservice.agent.execution.event.AgentProgressBroadcaster;
import com.javaee.aiservice.agent.execution.event.AgentProgressEvent;
import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentPlanStep;
import com.javaee.aiservice.agent.execution.model.AgentStepStatus;
import com.javaee.aiservice.agent.execution.model.AgentToolResult;
import com.javaee.aiservice.agent.execution.reflection.AgentReflection;
import com.javaee.aiservice.agent.execution.reflection.AgentReflectionService;
import com.javaee.aiservice.agent.execution.task.AgentTaskRegistry;
import com.javaee.aiservice.agent.execution.tool.AgentTool;
import com.javaee.aiservice.agent.execution.tool.AgentToolDefinition;
import com.javaee.aiservice.agent.execution.tool.AgentToolParameterDefinition;
import com.javaee.aiservice.agent.execution.tool.AgentToolRegistry;
import com.javaee.aiservice.agent.execution.tool.AgentToolSupport;
import com.javaee.aiservice.conversation.ContextManager;
import com.javaee.aiservice.conversation.ConversationManager;
import com.javaee.aiservice.internal.InternalService;
import com.javaee.aiservice.security.RequestUserContext;
import com.javaee.common.utils.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Product-level Agent chain: plan -> execute tools -> synthesize answer -> persist conversation.
 */
@Service
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatService chatService;

    private final ConversationManager conversationManager;

    private final ContextManager contextManager;

    private final InternalService internalService;

    private final AgentToolRegistry toolRegistry;

    private final AgentApprovalService agentApprovalService;

    private final RequestUserContext requestUserContext;

    private final AgentTaskRegistry taskRegistry;

    private final AgentProgressBroadcaster progressBroadcaster;

    private final AgentReflectionService reflectionService;

    private final PlaceholderResolver placeholderResolver = new PlaceholderResolver();

    @Autowired
    public AgentExecutionService(ChatService chatService, ConversationManager conversationManager,
                                 ContextManager contextManager, InternalService internalService,
                                 AgentToolRegistry toolRegistry, AgentApprovalService agentApprovalService,
                                 RequestUserContext requestUserContext, AgentTaskRegistry taskRegistry,
                                 AgentProgressBroadcaster progressBroadcaster, AgentReflectionService reflectionService) {
        this.chatService = chatService;
        this.conversationManager = conversationManager;
        this.contextManager = contextManager;
        this.internalService = internalService;
        this.toolRegistry = toolRegistry;
        this.agentApprovalService = agentApprovalService;
        this.requestUserContext = requestUserContext;
        this.taskRegistry = taskRegistry;
        this.progressBroadcaster = progressBroadcaster;
        this.reflectionService = reflectionService;
    }

    @Value("${ai.agent.reflection.enabled:true}")
    private boolean defaultReflectionEnabled;

    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(AgentExecutionRequest request) {
        validateRequest(request);

        long startedAt = System.currentTimeMillis();
        String userId = requestUserContext.getRequiredUserId();
        request.setUserId(userId);

        String continueTraceId = request.getContinueTraceId();
        Map<String, Object> existingSnapshot;
        String traceId;
        String conversationId;
        Map<String, Object> context;
        List<AgentPlanStep> plan;
        List<AgentToolResult> toolResults;
        List<AgentReflection> reflections;
        List<Map<String, Object>> timeline;
        Set<String> executedSignatures;
        int startIteration;
        int toolCallCount;
        int iterations;
        boolean requiresAction;
        String stoppedReason;

        if (continueTraceId != null && !continueTraceId.isBlank()) {
            existingSnapshot = taskRegistry.get(continueTraceId);
            if (existingSnapshot == null || existingSnapshot.isEmpty()) {
                throw new IllegalArgumentException("续接任务不存在: " + continueTraceId);
            }
            traceId = continueTraceId;
            conversationId = asString(existingSnapshot.get("conversationId"));
            context = existingSnapshot.get("context") instanceof Map<?, ?> raw
                    ? new HashMap<>((Map<String, Object>) raw)
                    : new HashMap<>();
            plan = existingSnapshot.get("plan") instanceof List<?> rawPlan
                    ? rawPlan.stream().map(this::toPlanStep).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>();
            toolResults = convertToolResults(existingSnapshot.get("toolResults"));
            reflections = existingSnapshot.get("reflections") instanceof List<?> rawRefl
                    ? rawRefl.stream().filter(AgentReflection.class::isInstance).map(AgentReflection.class::cast)
                    .collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>();
            timeline = existingSnapshot.get("timeline") instanceof List<?> rawTimeline
                    ? new ArrayList<>((List<Map<String, Object>>) rawTimeline)
                    : new ArrayList<>();
            executedSignatures = new LinkedHashSet<>();
            for (AgentToolResult tr : toolResults) {
                executedSignatures.add(tr.getToolName() + ":" + safeJson(tr.getData()));
            }
            iterations = intValue(existingSnapshot.get("iterations"), 0);
            int pendingIteration = firstRunnableIteration(plan);
            startIteration = pendingIteration > 0 ? pendingIteration : iterations + 1;
            toolCallCount = intValue(existingSnapshot.get("toolCallCount"), 0);
            requiresAction = false;
            stoppedReason = "continued";

            Map<String, Object> userSupplement = mergeContext(conversationId, request.getContext());
            userSupplement.put("userSupplement", request.getTask());
            context.putAll(userSupplement);
            context.put("traceId", traceId);
            context.put("userId", userId);
            context.putIfAbsent("bucketName", AgentToolSupport.bucketNameForUser(userId));

            log.info("续接Agent任务: traceId={}, startIteration={}, supplement={}", traceId, startIteration, request.getTask());
            publishTaskEvent("task_continued", traceId, userId, "running", progressOf(toolCallCount, Math.max(1, intValue(request.getMaxToolCalls(), 8))),
                    "Agent 任务续接，用户补充: " + request.getTask(), Map.of("supplement", request.getTask()));
        } else {
            traceId = UUID.randomUUID().toString();
            conversationId = ensureConversation(request.getConversationId(), userId);
            context = mergeContext(conversationId, request.getContext());
            context.put("userId", userId);
            context.put("role", requestUserContext.getCurrentRole());
            context.put("traceId", traceId);
            context.put("knowledgeBaseId", valueOrDefault(request.getKnowledgeBaseId(), "default"));
            context.putIfAbsent("bucketName", AgentToolSupport.bucketNameForUser(userId));

            log.info("开始执行Agent链路: traceId={}, conversationId={}, task={}", traceId, conversationId, request.getTask());
            publishTaskEvent("task_started", traceId, userId, "running", 0, "Agent 任务开始", Map.of("task", request.getTask()));

            plan = new ArrayList<>();
            toolResults = new ArrayList<>();
            reflections = new ArrayList<>();
            timeline = new ArrayList<>();
            executedSignatures = new LinkedHashSet<>();
            startIteration = 1;
            toolCallCount = 0;
            iterations = 0;
            requiresAction = false;
            stoppedReason = "completed";
        }

        int maxIterations = Math.clamp(intValue(request.getMaxIterations(), 3), 1, 5);
        int maxToolCalls = Math.clamp(intValue(request.getMaxToolCalls(), 8), 1, 20);
        boolean simpleTask = false;
        List<AgentPlanStep> reflectionPlan = List.of();

        for (int iteration = startIteration; iteration <= maxIterations; iteration++) {
            iterations = iteration;
            if (taskRegistry.isCancelled(traceId)) {
                stoppedReason = "cancelled";
                publishTaskEvent("task_cancelled", traceId, userId, "cancelled", 100, "Agent 任务已取消", Map.of());
                break;
            }
            List<AgentPlanStep> iterationPlan = runnablePlanForIteration(plan, iteration);
            if (iterationPlan.isEmpty()) {
                iterationPlan = buildIterationPlan(request, context, plan, toolResults, iteration, reflectionPlan);
                iterationPlan = registerIterationPlan(plan, iterationPlan, iteration);
                reflectionPlan = List.of();
            }
            if (iterationPlan.isEmpty()) {
                stoppedReason = "no_plan";
                break;
            }

            for (AgentPlanStep step : iterationPlan) {
                if (AgentStepStatus.isTerminal(step.getStatus())) {
                    continue;
                }
                refreshStepParamsFromContext(step, context);
                String approvalToken = approvalTokenFrom(request);
                if (!isBlank(approvalToken)) {
                    step.getParams().put("agentApprovalToken", approvalToken);
                }

                if (Boolean.TRUE.equals(request.getDryRun())) {
                    step.setStatus(AgentStepStatus.PLANNED.value());
                    publishTaskEvent("step_planned", traceId, userId, step.getStatus(), progressOf(plan.size(), maxToolCalls),
                            "步骤已规划: " + step.getToolName(), timelineEvent(iteration, step, null));
                    continue;
                }

                if (toolCallCount >= maxToolCalls) {
                    AgentToolResult result = AgentToolResult.error(step.getToolName(), "已达到工具调用上限: " + maxToolCalls);
                    decorateResult(result, step, System.currentTimeMillis());
                    step.setStatus(result.getStatus());
                    step.setObservation(result.getMessage());
                    toolResults.add(result);
                    stoppedReason = "tool_call_limit";
                    break;
                }

                if (!areDependenciesSatisfied(step, plan)) {
                    AgentToolResult result = AgentToolResult.error(step.getToolName(),
                            "依赖步骤未成功完成: " + step.getDependsOn());
                    decorateResult(result, step, System.currentTimeMillis());
                    step.setStatus(AgentStepStatus.BLOCKED.value());
                    step.setObservation(result.getMessage());
                    toolResults.add(result);
                    timeline.add(timelineEvent(iteration, step, result));
                    stoppedReason = "dependency_failed";
                    break;
                }

                String signature = step.getToolName() + ":" + safeJson(step.getParams());
                if (executedSignatures.contains(signature) && !"direct-answer".equals(step.getToolName())) {
                    step.setStatus(AgentStepStatus.SKIPPED.value());
                    step.setObservation("跳过重复工具调用");
                    timeline.add(timelineEvent(iteration, step, null));
                    continue;
                }
                executedSignatures.add(signature);

                removePreviousActionRequiredResult(toolResults, step);
                AgentToolResult result = runStepWithRetry(step, request, context, iteration, timeline);
                publishTaskEvent("step_finished", traceId, userId, step.getStatus(), progressOf(toolCallCount + 1, maxToolCalls),
                        result.getMessage(), timelineEvent(iteration, step, result));
                toolResults.add(result);
                toolCallCount++;
                mergeToolResultIntoContext(context, result, step);
                internalService.logAudit(step.getToolName(), step.getParams(), objectMapper.convertValue(result, new TypeReference<>() {}));

                if ("error".equals(result.getStatus()) || result.isRequiresAction()) {
                    requiresAction = result.isRequiresAction();
                    stoppedReason = result.isRequiresAction() ? "action_required" : "tool_error";
                    break;
                }
            }

            if (Boolean.TRUE.equals(request.getDryRun())) {
                stoppedReason = "dry_run";
                break;
            }

            if (requiresAction || "tool_call_limit".equals(stoppedReason)) {
                break;
            }

            simpleTask = isSimpleTask(plan, request);
            AgentReflection reflection = simpleTask ? null : reflectAfterIteration(request, plan, toolResults, context,
                    iteration, maxIterations, traceId, userId);
            if (reflection != null) {
                reflections.add(reflection);
                context.put("lastReflection", reflection);
                timeline.add(reflectionTimelineEvent(iteration, reflection));
            }

            if (iteration >= maxIterations) {
                if (reflection != null && !reflection.isComplete()) {
                    stoppedReason = "max_iterations";
                } else if (!"tool_error".equals(stoppedReason)
                        && !"dependency_failed".equals(stoppedReason)) {
                    stoppedReason = "completed";
                }
                break;
            }
            if (!Boolean.TRUE.equals(request.getAutoReplan())) {
                if (!"tool_error".equals(stoppedReason) && !"dependency_failed".equals(stoppedReason)) {
                    stoppedReason = "completed";
                }
                break;
            }

            if (isReflectionEnabled(request)) {
                if (reflection == null || reflection.isComplete() || !reflection.isContinueExecution()) {
                    if (!"tool_error".equals(stoppedReason) && !"dependency_failed".equals(stoppedReason)) {
                        stoppedReason = "completed";
                    }
                    break;
                }
                reflectionPlan = toolRegistry.normalizePlan(reflection.getRevisedPlan(), request, context);
                if (reflectionPlan.isEmpty()) {
                    stoppedReason = "follow_up_replan";
                    continue;
                }
                stoppedReason = "reflection_replan";
                continue;
            }

            if ("tool_error".equals(stoppedReason) || "dependency_failed".equals(stoppedReason)) {
                break;
            }
            if (!shouldContinue(request, plan, toolResults, context, iteration, maxIterations)) {
                stoppedReason = "completed";
                break;
            }
        }

        String finalAnswer = synthesizeAnswer(request, plan, toolResults, context, requiresAction, simpleTask);
        conversationManager.addMessageForUser(conversationId, userId, request.getTask(), finalAnswer);
        context.put("lastAnswer", finalAnswer);
        context.put("lastToolResults", toolResults);
        context.put("lastReflections", reflections);
        contextManager.updateContext(conversationId, context);

        Map<String, Object> pendingApproval = requiresAction ? pendingApprovalFrom(plan, toolResults) : Map.of();
        Map<String, Object> pendingUserInput = requiresAction ? pendingUserInputFrom(plan, toolResults, traceId) : Map.of();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", requiresAction ? "action_required" : resolveStatus(toolResults));
        response.put("traceId", traceId);
        response.put("conversationId", conversationId);
        response.put("task", request.getTask());
        response.put("plan", plan);
        response.put("toolResults", toolResults);
        response.put("reflections", Boolean.FALSE.equals(request.getReturnIntermediateSteps()) ? List.of() : reflections);
        response.put("timeline", Boolean.FALSE.equals(request.getReturnIntermediateSteps()) ? List.of() : timeline);
        response.put("answer", finalAnswer);
        response.put("context", contextManager.getContext(conversationId));
        response.put("availableTools", toolRegistry.list());
        response.put("iterations", iterations);
        response.put("toolCallCount", toolCallCount);
        response.put("stoppedReason", stoppedReason);
        response.put("durationMs", System.currentTimeMillis() - startedAt);
        response.put("userId", userId);
        if (!pendingApproval.isEmpty()) {
            response.put("pendingApproval", pendingApproval);
        } else if (!pendingUserInput.isEmpty()) {
            response.put("pendingUserInput", pendingUserInput);
        }
        taskRegistry.save(traceId, response);
        if (requiresAction) {
            publishTaskEvent("task_waiting_user", traceId, userId, String.valueOf(response.get("status")), 95,
                    "Agent 任务等待用户确认或补充信息", response);
        } else {
            publishTaskEvent("task_finished", traceId, userId, String.valueOf(response.get("status")), 100,
                    "Agent 任务结束: " + stoppedReason, response);
        }
        return response;
    }

    public List<AgentToolDefinition> listTools() {
        return toolRegistry.list();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> confirmApproval(AgentExecutionRequest request) {
        String userId = requestUserContext.getRequiredUserId();
        String token = approvalTokenFrom(request);
        Map<String, Object> snapshot = taskRegistry.findByApprovalToken(token);
        if (snapshot.isEmpty()) {
            return Map.of("status", "not_found", "message", "未找到待确认任务或审批已失效");
        }
        assertSnapshotOwner(snapshot, userId, "无权确认该任务");

        Map<String, Object> pending = snapshot.get("pendingApproval") instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw)
                : new LinkedHashMap<>();

        AgentExecutionRequest resumed = new AgentExecutionRequest();
        resumed.setTask(asString(snapshot.get("task")));
        resumed.setConversationId(asString(snapshot.get("conversationId")));
        resumed.setModel(request == null ? null : request.getModel());
        resumed.setContinueTraceId(asString(snapshot.get("traceId")));
        resumed.setApprovalToken(token);
        resumed.setReflectionEnabled(request == null ? null : request.getReflectionEnabled());
        resumed.setAutoReplan(request == null ? null : request.getAutoReplan());
        resumed.setMaxIterations(request == null ? null : request.getMaxIterations());
        resumed.setMaxToolCalls(request == null ? null : request.getMaxToolCalls());
        resumed.setReturnIntermediateSteps(request == null ? null : request.getReturnIntermediateSteps());
        Map<String, Object> resumedContext = snapshot.get("context") instanceof Map<?, ?> raw
                ? new HashMap<>((Map<String, Object>) raw)
                : new HashMap<>();
        resumedContext.put("agentApprovalToken", token);
        resumedContext.put("approvalToken", token);
        resumedContext.put("userId", userId);
        mergeLatestApprovalContext(resumedContext, request);
        resumed.setContext(resumedContext);
        if (pending.isEmpty()) {
            return Map.of("status", "not_found", "message", "未找到待确认步骤", "traceId", snapshot.get("traceId"));
        }

        return execute(resumed);
    }

    public boolean cancelApproval(String token, String userId) {
        Map<String, Object> snapshot = taskRegistry.findByApprovalToken(token);
        boolean removed = agentApprovalService.cancel(token);
        if (snapshot.isEmpty()) {
            return removed;
        }
        assertSnapshotOwner(snapshot, userId, "无权取消该任务");
        snapshot.put("status", "cancelled");
        snapshot.put("stoppedReason", "approval_cancelled");
        snapshot.put("pendingApproval", null);
        snapshot.put("cancelledAt", System.currentTimeMillis());
        taskRegistry.save(asString(snapshot.get("traceId")), snapshot);
        publishTaskEvent("task_cancelled", asString(snapshot.get("traceId")), userId, "cancelled", 100,
                "用户已否定高危操作，Agent 任务取消", snapshot);
        return true;
    }

    private void publishTaskEvent(String eventType, String traceId, String userId, String status,
                                  int progress, String message, Map<String, Object> payload) {
        if (progressBroadcaster == null) {
            return;
        }
        AgentProgressEvent event = AgentProgressEvent.of(eventType, userId, status, message);
        event.setTraceId(traceId);
        event.setProgress(progress);
        event.setPayload(payload);
        progressBroadcaster.publish(event);
    }

    private Map<String, Object> pendingApprovalFrom(List<AgentPlanStep> plan, List<AgentToolResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return Map.of();
        }
        AgentToolResult last = toolResults.getLast();
        if (!last.isRequiresAction() || last.getData() == null || !last.getData().containsKey("agentApprovalToken")) {
            return Map.of();
        }
        AgentPlanStep step = findStep(plan, last.getStepId());
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("agentApprovalToken", last.getData().get("agentApprovalToken"));
        pending.put("expiresAt", last.getData().get("expiresAt"));
        pending.put("stepId", last.getStepId());
        pending.put("toolName", last.getToolName());
        pending.put("params", step == null ? last.getData().get("params") : step.getParams());
        pending.put("message", last.getMessage());
        pending.put("createdAt", System.currentTimeMillis());
        return pending;
    }

    private Map<String, Object> pendingUserInputFrom(List<AgentPlanStep> plan,
                                                     List<AgentToolResult> toolResults,
                                                     String traceId) {
        if (toolResults == null || toolResults.isEmpty()) {
            return Map.of();
        }
        AgentToolResult last = toolResults.getLast();
        if (!last.isRequiresAction() || last.getData() == null || last.getData().containsKey("agentApprovalToken")) {
            return Map.of();
        }
        AgentPlanStep step = findStep(plan, last.getStepId());
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("interactionType", valueOrDefault(asString(last.getData().get("interactionType")), "user_input"));
        pending.put("resumeMode", "continue_trace");
        pending.put("traceId", traceId);
        pending.put("stepId", last.getStepId());
        pending.put("toolName", last.getToolName());
        pending.put("question", firstNonBlank(asString(last.getData().get("question")), last.getMessage()));
        pending.put("missingParameters", last.getData().getOrDefault("missingParameters", List.of()));
        pending.put("invalidParameters", last.getData().getOrDefault("invalidParameters", Map.of()));
        pending.put("missingFields", last.getData().getOrDefault("missingFields", List.of()));
        pending.put("options", last.getData().getOrDefault("options", List.of()));
        pending.put("params", step == null ? Map.of() : step.getParams());
        pending.put("resumeEndpoint", "/api/ai/agent/tasks/" + traceId + "/continue");
        pending.put("createdAt", System.currentTimeMillis());
        return pending;
    }

    private String approvalTokenFrom(AgentExecutionRequest request) {
        if (request == null) {
            return null;
        }
        String directToken = asString(request.getApprovalToken());
        if (!isBlank(directToken)) {
            return directToken;
        }
        if (request.getContext() == null) {
            return null;
        }
        return firstNonBlank(
                asString(request.getContext().get("agentApprovalToken")),
                asString(request.getContext().get("approvalToken"))
        );
    }

    private void mergeLatestApprovalContext(Map<String, Object> targetContext, AgentExecutionRequest request) {
        if (request == null || request.getContext() == null || request.getContext().isEmpty()) {
            return;
        }
        targetContext.putAll(request.getContext());
    }

    private void refreshStepParamsFromContext(AgentPlanStep step, Map<String, Object> context) {
        if (step == null || context == null || context.isEmpty()) {
            return;
        }
        Map<String, Object> params = step.getParams();
        for (String key : toolRegistry.contextOverrideKeys(step.getToolName())) {
            Object value = context.get(key);
            if (!isValueMissing(value)) {
                params.put(key, value);
            }
        }
    }

    private void assertSnapshotOwner(Map<String, Object> snapshot, String userId, String message) {
        String owner = asString(snapshot.get("userId"));
        if (!requestUserContext.isAdmin() && !valueOrDefault(owner, "").equals(userId)) {
            throw new SecurityException(message);
        }
    }

    private AgentPlanStep findStep(Object planObj, String stepId) {
        if (planObj instanceof List<?> list) {
            for (Object item : list) {
                AgentPlanStep step = toPlanStep(item);
                if (step != null && (stepId == null || stepId.equals(step.getId()))) {
                    return step;
                }
            }
        }
        return null;
    }

    private AgentPlanStep toPlanStep(Object item) {
        if (item instanceof AgentPlanStep step) {
            return step;
        }
        if (item instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, AgentPlanStep.class);
        }
        return null;
    }

    private List<AgentToolResult> convertToolResults(Object toolResultsObj) {
        List<AgentToolResult> results = new ArrayList<>();
        if (toolResultsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof AgentToolResult result) {
                    results.add(result);
                } else if (item instanceof Map<?, ?> map) {
                    results.add(objectMapper.convertValue(map, AgentToolResult.class));
                }
            }
        }
        return results;
    }

    private int progressOf(int current, int total) {
        int safeTotal = Math.max(1, total);
        return Math.clamp((int) Math.round((current * 100.0) / safeTotal), 0, 95);
    }

    private Map<String, Object> reflectionTimelineEvent(int iteration, AgentReflection reflection) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "reflection");
        event.put("iteration", iteration);
        event.put("complete", reflection.isComplete());
        event.put("continueExecution", reflection.isContinueExecution());
        event.put("requiresReplan", reflection.isRequiresReplan());
        event.put("qualityScore", reflection.getQualityScore());
        event.put("confidence", reflection.getConfidence());
        event.put("reason", reflection.getReason());
        event.put("issues", reflection.getIssues());
        event.put("missingInfo", reflection.getMissingInfo());
        event.put("recommendedActions", reflection.getRecommendedActions());
        event.put("revisedPlan", reflection.getRevisedPlan());
        event.put("source", reflection.getSource());
        event.put("fallback", reflection.isFallback());
        event.put("createdAt", reflection.getCreatedAt());
        return event;
    }

    private boolean isReflectionEnabled(AgentExecutionRequest request) {
        return request.getReflectionEnabled() == null
                ? defaultReflectionEnabled
                : Boolean.TRUE.equals(request.getReflectionEnabled());
    }

    /**
     * 对历史任务的单个步骤进行重试，返回最新工具结果与原任务快照。
     * 仅用于已结束（success/error/cancelled）任务的针对性补救：不会触发整轮 replan，
     * 也不会修改原始 timeline，结果通过 `singleStepRetry` 字段附加到 snapshot。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> retryStep(String traceId, String stepId, Map<String, Object> overrideParams) {
        Map<String, Object> snapshot = taskRegistry.get(traceId);
        if (snapshot == null || snapshot.isEmpty()) {
            return Map.of("status", "not_found", "traceId", traceId);
        }
        Object planObj = snapshot.get("plan");
        if (!(planObj instanceof List<?> planList)) {
            return Map.of("status", "no_plan", "traceId", traceId);
        }
        AgentPlanStep target = null;
        for (Object item : planList) {
            if (item instanceof AgentPlanStep s && stepId.equals(s.getId())) {
                target = s;
                break;
            }
        }
        if (target == null) {
            return Map.of("status", "step_not_found", "traceId", traceId, "stepId", stepId);
        }
        if (overrideParams != null && !overrideParams.isEmpty()) {
            target.getParams().putAll(overrideParams);
        }
        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setTask(asString(snapshot.get("task")));
        request.setUserId(asString(snapshot.get("userId")));
        request.setConversationId(asString(snapshot.get("conversationId")));
        Map<String, Object> ctx = snapshot.get("context") instanceof Map<?, ?> raw
                ? new HashMap<>((Map<String, Object>) raw)
                : new HashMap<>();
        AgentToolResult result = runStepOnce(target, request, ctx);
        target.setAttempts(target.getAttempts() + 1);

        Map<String, Object> retryInfo = new LinkedHashMap<>();
        retryInfo.put("stepId", stepId);
        retryInfo.put("status", result.getStatus());
        retryInfo.put("observation", result.getMessage());
        retryInfo.put("data", result.getData());
        retryInfo.put("attempts", target.getAttempts());
        retryInfo.put("retriedAt", System.currentTimeMillis());
        snapshot.put("singleStepRetry", retryInfo);
        taskRegistry.save(traceId, snapshot);

        Map<String, Object> response = new LinkedHashMap<>(retryInfo);
        response.put("traceId", traceId);
        return response;
    }

    private List<AgentPlanStep> registerIterationPlan(List<AgentPlanStep> plan,
                                                      List<AgentPlanStep> iterationPlan,
                                                      int iteration) {
        if (iterationPlan == null || iterationPlan.isEmpty()) {
            return List.of();
        }
        List<AgentPlanStep> prepared = new ArrayList<>();
        int index = 1;
        for (AgentPlanStep step : iterationPlan) {
            String id = valueOrDefault(step.getId(), "step-" + (plan.size() + index));
            if (!id.startsWith("iter-")) {
                id = "iter-" + iteration + "-" + id;
            }
            step.setId(id);
            if (isBlank(step.getStatus())) {
                step.setStatus(AgentStepStatus.PENDING.value());
            }
            plan.add(step);
            prepared.add(step);
            index++;
        }
        return prepared;
    }

    private int firstRunnableIteration(List<AgentPlanStep> plan) {
        int first = Integer.MAX_VALUE;
        for (AgentPlanStep step : plan) {
            if (isRunnableStep(step)) {
                int iteration = iterationOf(step);
                if (iteration > 0) {
                    first = Math.min(first, iteration);
                }
            }
        }
        return first == Integer.MAX_VALUE ? -1 : first;
    }

    private List<AgentPlanStep> runnablePlanForIteration(List<AgentPlanStep> plan, int iteration) {
        List<AgentPlanStep> runnable = new ArrayList<>();
        for (AgentPlanStep step : plan) {
            if (isRunnableStep(step) && iterationOf(step) == iteration) {
                runnable.add(step);
            }
        }
        return runnable;
    }

    private boolean isRunnableStep(AgentPlanStep step) {
        return step != null && !AgentStepStatus.isTerminal(step.getStatus());
    }

    private int iterationOf(AgentPlanStep step) {
        if (step == null || isBlank(step.getId()) || !step.getId().startsWith("iter-")) {
            return -1;
        }
        String rest = step.getId().substring("iter-".length());
        int dash = rest.indexOf('-');
        if (dash <= 0) {
            return -1;
        }
        try {
            return Integer.parseInt(rest.substring(0, dash));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void removePreviousActionRequiredResult(List<AgentToolResult> toolResults, AgentPlanStep step) {
        if (!AgentStepStatus.WAITING_USER.value().equals(step.getStatus())) {
            return;
        }
        String stepId = step.getId();
        toolResults.removeIf(result -> stepId != null && stepId.equals(result.getStepId()) && result.isRequiresAction());
    }

    private List<AgentPlanStep> buildIterationPlan(AgentExecutionRequest request, Map<String, Object> context,
                                                   List<AgentPlanStep> previousPlan,
                                                   List<AgentToolResult> previousResults,
                                                   int iteration,
                                                   List<AgentPlanStep> reflectedPlan) {
        if (reflectedPlan != null && !reflectedPlan.isEmpty()) {
            return reflectedPlan;
        }
        if (iteration == 1) {
            return buildPlan(request, context);
        }
        try {
            String raw = chatService.callChatApiWithModelCode(
                    buildFollowUpPlannerPrompt(request, context, previousPlan, previousResults, iteration),
                    request.getModel());
            List<AgentPlanStep> plan = parsePlan(raw);
            if (!plan.isEmpty()) {
                return toolRegistry.normalizePlan(plan, request, context);
            }
        } catch (Exception e) {
            log.warn("模型补充规划失败，结束自动重规划: {}", e.getMessage());
        }
        return List.of();
    }

    private AgentReflection reflectAfterIteration(AgentExecutionRequest request, List<AgentPlanStep> plan,
                                                 List<AgentToolResult> results, Map<String, Object> context,
                                                 int iteration, int maxIterations, String traceId, String userId) {
        if (!isReflectionEnabled(request) || results.isEmpty()) {
            return null;
        }
        AgentReflection reflection = reflectionService.reflect(request, plan, results, context, iteration, maxIterations);
        if (reflection == null) {
            return null;
        }
        publishTaskEvent("reflection_finished", traceId, userId,
                reflection.isComplete() ? "complete" : "needs_more_work",
                progressOf(iteration, maxIterations),
                valueOrDefault(reflection.getReason(), "Agent 反思完成"),
                objectMapper.convertValue(reflection, new TypeReference<>() {}));
        return reflection;
    }

    private String buildFollowUpPlannerPrompt(AgentExecutionRequest request, Map<String, Object> context,
                                              List<AgentPlanStep> previousPlan,
                                              List<AgentToolResult> previousResults,
                                              int iteration) {
        return """
                你是Dockit的Agent执行器，正在进行第%s轮补充规划。请只输出JSON数组，不要输出解释、Markdown或代码块。
                如果任务已经可以回答，请输出空数组 []。
                如果还需要工具，请输出最多3个后续步骤。
                %s
                只能使用工具列表中的toolName；不要重复已经成功执行且参数相同的工具。
                如果后续步骤要消费前面步骤的结果，请使用 ${steps.<id>.data.<key>} 或 ${steps.<id>.observation}，并在 dependsOn 中写入对应 id。

                工具列表:
                %s

                用户任务:
                %s

                已执行计划:
                %s

                工具观察结果:
                %s

                当前上下文:
                %s
                """.formatted(iteration, plannerJsonContract(), toolCatalog(), request.getTask(), safeJson(previousPlan),
                safeJson(previousResults), safeJson(context));
    }

    private boolean shouldContinue(AgentExecutionRequest request, List<AgentPlanStep> plan,
                                   List<AgentToolResult> results, Map<String, Object> context,
                                   int iteration, int maxIterations) {
        if (iteration >= maxIterations || results.isEmpty()) {
            return false;
        }
        AgentToolResult last = results.getLast();
        if ("error".equals(last.getStatus()) || last.isRequiresAction()) {
            return false;
        }
        if ("direct-answer".equals(last.getToolName())) {
            return false;
        }
        if (hasFinalAnswerSignal(last)) {
            return false;
        }

        try {
            String prompt = """
                    你是Dockit Agent的完成度评估器。请只输出JSON对象，不要输出解释。
                    格式: {"continue":true/false,"reason":"简短原因"}

                    判断规则:
                    - 如果工具结果已经足够回答用户任务，continue=false。
                    - 如果还缺少必要信息、需要追加检索、需要继续调用工具，continue=true。
                    - 不要为了无意义优化继续循环。

                    用户任务:
                    %s

                    当前计划:
                    %s

                    工具结果:
                    %s

                    上下文:
                    %s
                    """.formatted(request.getTask(), safeJson(plan), safeJson(results), safeJson(context));
            String raw = chatService.callChatApiWithModelCode(prompt, request.getModel());
            Map<String, Object> decision = objectMapper.readValue(stripObjectJson(raw), new TypeReference<>() {});
            return booleanValue(decision.get("continue"));
        } catch (Exception e) {
            log.debug("完成度评估失败，默认结束当前Agent循环: {}", e.getMessage());
            return false;
        }
    }

    private boolean hasFinalAnswerSignal(AgentToolResult result) {
        return result.getData().containsKey("answer")
                || result.getData().containsKey("fileUrl")
                || result.getData().containsKey("result");
    }

    private void decorateResult(AgentToolResult result, AgentPlanStep step, long startedAt) {
        long finishedAt = System.currentTimeMillis();
        result.setStepId(step.getId());
        result.setStartedAt(startedAt);
        result.setFinishedAt(finishedAt);
        result.setDurationMs(Math.max(0, finishedAt - startedAt));
    }

    private Map<String, Object> timelineEvent(int iteration, AgentPlanStep step, AgentToolResult result) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("iteration", iteration);
        event.put("stepId", step.getId());
        event.put("description", step.getDescription());
        event.put("toolName", step.getToolName());
        event.put("status", step.getStatus());
        event.put("observation", step.getObservation());
        if (result != null) {
            event.put("durationMs", result.getDurationMs());
            event.put("resultSummary", result.getMessage());
        }
        return event;
    }

    @SuppressWarnings("unchecked")
    private void mergeToolResultIntoContext(Map<String, Object> context, AgentToolResult result, AgentPlanStep step) {
        context.put("lastTool", result.getToolName());
        context.put("lastToolStatus", result.getStatus());
        context.put("lastObservation", result.getMessage());
        if ("success".equals(result.getStatus())) {
            for (Map.Entry<String, Object> entry : result.getData().entrySet()) {
                if (entry.getValue() != null && isSafeContextKey(entry.getKey())) {
                    context.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (step != null && step.getId() != null) {
            Object existing = context.get("__stepResults__");
            Map<String, Object> stepResults = existing instanceof Map
                    ? (Map<String, Object>) existing
                    : new LinkedHashMap<>();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("status", result.getStatus());
            snapshot.put("observation", result.getMessage());
            snapshot.put("data", result.getData());
            stepResults.put(step.getId(), snapshot);
            // 同时按短 id（去掉 iter- 前缀）建立别名，方便模板引用
            String shortId = step.getId().contains("-")
                    ? step.getId().substring(step.getId().lastIndexOf('-') + 1)
                    : step.getId();
            stepResults.putIfAbsent(shortId, snapshot);
            context.put("__stepResults__", stepResults);
        }
    }

    private boolean isSafeContextKey(String key) {
        return Set.of("answer", "sources", "retrieved", "results", "fileUrl", "objectName",
                "bucketName", "recycleId", "versionId", "documentId", "documentTitle",
                "documentContent", "documentVersion", "query", "frontendAction",
                "requiresFrontendWrite", "writeMode", "changeLog", "persisted", "insertAfterText").contains(key);
    }

    private void validateRequest(AgentExecutionRequest request) {
        if (request == null || isBlank(request.getTask())) {
            throw new IllegalArgumentException("任务描述不能为空");
        }
    }

    /**
     * 简单任务判定：单步计划、非 autoReplan 且步骤已成功。
     * 简单任务跳过反思与完成度评估，直接汇总工具结果，减少一次模型往返。
     */
    private boolean isSimpleTask(List<AgentPlanStep> plan, AgentExecutionRequest request) {
        if (Boolean.TRUE.equals(request.getAutoReplan())) {
            return false;
        }
        return plan != null && plan.size() == 1
                && plan.getFirst() != null
                && AgentStepStatus.isSuccess(plan.getFirst().getStatus());
    }

    private String ensureConversation(String conversationId, String userId) {
        if (!isBlank(conversationId)) {
            conversationManager.assertOwner(conversationId, userId);
            return conversationId;
        }
        return conversationManager.createConversation(userId);
    }

    private Map<String, Object> mergeContext(String conversationId, Map<String, Object> requestContext) {
        Map<String, Object> merged = new HashMap<>(contextManager.getContext(conversationId));
        if (requestContext != null) {
            merged.putAll(requestContext);
        }
        return merged;
    }

    private List<AgentPlanStep> buildPlan(AgentExecutionRequest request, Map<String, Object> context) {
        if (!isBlank(asString(context.get("documentId"))) && AgentToolSupport.isDeleteIntent(request.getTask())) {
            return FallbackPlanner.fallbackPlan(request, context, toolRegistry);
        }
        if (booleanValue(context.get("frontendDocumentWrite")) || !isBlank(asString(context.get("objectName")))) {
            return FallbackPlanner.fallbackPlan(request, context, toolRegistry);
        }
        try {
            String prompt = buildPlannerPrompt(request, context);
            String raw = chatService.callChatApiWithModelCode(prompt, request.getModel());
            List<AgentPlanStep> plan = parsePlan(raw);
            if (!plan.isEmpty()) {
                return toolRegistry.normalizePlan(plan, request, context);
            }
        } catch (Exception e) {
            log.warn("模型规划失败，使用规则兜底规划: {}", e.getMessage());
        }
        return FallbackPlanner.fallbackPlan(request, context, toolRegistry);
    }

    private String plannerJsonContract() {
        return """
                计划JSON字段契约:
                - 必填字段: id, description, toolName, params, reasoning。
                - 可选字段: dependsOn, successCriteria, retryPolicy, maxRetries, riskLevel。
                - dependsOn 必须引用同一个计划中更早步骤的 id，例如 ["search"]；后续步骤依赖前序工具结果时必须填写。
                - successCriteria 用于声明工具成功判据，可写 contains:关键词、data.<key>、data.<key>=value，多条用分号分隔。
                - retryPolicy 只能使用 none 或 exponential；危险操作使用 none，普通查询/生成可使用 exponential。
                - maxRetries 是整数，普通工具建议 1，危险操作必须 0。
                - 后续步骤需要引用前序结果时，params 里可以使用占位符: ${task}, ${context.<key>}, ${steps.<id>.observation}, ${steps.<id>.data.<key>}。
                - 使用 ${steps.<id>.data.<key>} 时，该步骤必须在 dependsOn 中依赖产生结果的 <id>。
                - RAG 工具的 rerankStrategy 只是候选片段重排序策略，允许 HYBRID/VECTOR/BM25；通常省略或用 HYBRID，不要把它当成业务检索策略。
                """;
    }

    private String buildPlannerPrompt(AgentExecutionRequest request, Map<String, Object> context) {
        return """
                你是Dockit的任务规划器。请只输出JSON数组，不要输出解释、Markdown或代码块。
                %s
                只能从工具列表中选择toolName；如不需要工具，使用direct-answer。
                多步任务可以输出多个步骤，但不要超过5步；参数必须来自用户任务或上下文，不确定时先用direct-answer询问澄清。
                对危险操作(file-restore,file-version-switch)必须等待用户确认；file-delete 在前端已确认且 documentId 或 objectName 明确时可执行永久删除，不进入回收站。不要规划文件上传，文件上传必须由用户通过页面或上传接口自行完成。
                document-write 和 text-to-file 只返回前端写入补丁，不会保存MinIO；只有用户明确要求写入当前文档或上下文 frontendDocumentWrite=true 时才使用。

                工具列表:
                %s

                用户任务:
                %s

                上下文:
                %s
                """.formatted(plannerJsonContract(), toolCatalog(), request.getTask(), safeJson(context));
    }

    private String toolCatalog() {
        StringBuilder tools = new StringBuilder();
        for (AgentToolDefinition tool : toolRegistry.list()) {
            tools.append("- ").append(tool.getName()).append(": ").append(tool.getDescription())
                    .append(" 类别: ").append(tool.getCategory())
                    .append(" 风险等级: ").append(tool.getRiskLevel())
                    .append(" 危险操作: ").append(tool.isDestructive())
                    .append(" 需要用户动作: ").append(tool.isRequiresUserAction())
                    .append(" 参数schema: ").append(safeJson(tool.getParameterSchema())).append("\n");
        }
        tools.append("提示: 如果用户任务缺少必填参数或存在歧义，请优先选择 ask-user 工具向用户澄清，不要凭空填充参数。\n");
        return tools.toString();
    }

    private boolean areDependenciesSatisfied(AgentPlanStep step, List<AgentPlanStep> plan) {
        if (step.getDependsOn() == null || step.getDependsOn().isEmpty()) {
            return true;
        }
        Map<String, AgentPlanStep> indexed = new HashMap<>();
        for (AgentPlanStep prior : plan) {
            indexed.put(prior.getId(), prior);
            if (prior.getId() != null && prior.getId().contains("-")) {
                indexed.putIfAbsent(prior.getId().substring(prior.getId().lastIndexOf('-') + 1), prior);
            }
        }
        for (String dependency : step.getDependsOn()) {
            AgentPlanStep prior = indexed.get(dependency);
            if (prior == null || !AgentStepStatus.isSuccess(prior.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private AgentToolResult runStepWithRetry(AgentPlanStep step, AgentExecutionRequest request,
                                             Map<String, Object> context, int iteration,
                                             List<Map<String, Object>> timeline) {
        int maxAttempts = Math.max(1, step.getMaxRetries() + 1);
        AgentToolResult lastResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            step.setAttempts(attempt);
            AgentToolResult result = runStepOnce(step, request, context);

            if (AgentStepStatus.SUCCESS.value().equals(result.getStatus()) && !isBlank(step.getSuccessCriteria())) {
                String unmet = evaluateSuccessCriteria(step, result);
                if (unmet != null) {
                    AgentToolResult downgraded = AgentToolResult.error(step.getToolName(), "未满足 successCriteria: " + unmet);
                    downgraded.setData(result.getData());
                    decorateResult(downgraded, step, result.getStartedAt());
                    step.setStatus(AgentStepStatus.ERROR.value());
                    step.setObservation(downgraded.getMessage());
                    step.setThought("结果未达到 successCriteria，准备重试或 replan: " + unmet);
                    timeline.add(timelineEvent(iteration, step, downgraded));
                    lastResult = downgraded;
                    if ("none".equalsIgnoreCase(step.getRetryPolicy()) || attempt >= maxAttempts) {
                        step.setLastError(downgraded.getMessage());
                        return downgraded;
                    }
                    step.setLastError(downgraded.getMessage());
                    sleepBackoff(step.getRetryPolicy(), attempt);
                    continue;
                }
            }

            step.setThought(buildThought(step, result, attempt, maxAttempts));
            timeline.add(timelineEvent(iteration, step, result));
            lastResult = result;

            if (!"error".equals(result.getStatus()) || result.isRequiresAction()) {
                return result;
            }
            if ("none".equalsIgnoreCase(step.getRetryPolicy()) || attempt >= maxAttempts) {
                step.setLastError(result.getMessage());
                return result;
            }
            step.setLastError(result.getMessage());
            sleepBackoff(step.getRetryPolicy(), attempt);
        }
        return lastResult;
    }

    /**
     * 评估 successCriteria：支持以分号分隔的若干断言：
     * - contains:keyword       observation 中必须包含 keyword
     * - data.<key>             data 中必须存在该字段且非空
     * - data.<key>=value       data.key 必须等于 value
     * 返回 null 表示通过，否则返回未满足的条目。
     */
    String evaluateSuccessCriteria(AgentPlanStep step, AgentToolResult result) {
        String criteria = step.getSuccessCriteria();
        if (isBlank(criteria)) {
            return null;
        }
        String observation = valueOrDefault(result.getMessage(), "");
        Map<String, Object> data = result.getData() == null ? Map.of() : result.getData();
        for (String raw : criteria.split("[;；]")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("contains:")) {
                String keyword = token.substring("contains:".length()).trim();
                if (!observation.contains(keyword) && !safeJson(data).contains(keyword)) {
                    return token;
                }
            } else if (token.startsWith("data.")) {
                String expr = token.substring("data.".length());
                String key;
                String expected = null;
                int eq = expr.indexOf('=');
                if (eq > 0) {
                    key = expr.substring(0, eq).trim();
                    expected = expr.substring(eq + 1).trim();
                } else {
                    key = expr.trim();
                }
                Object actual = data.get(key);
                if (actual == null || actual.toString().isBlank()) {
                    return token;
                }
                if (expected != null && !expected.equals(actual.toString())) {
                    return token;
                }
            }
        }
        return null;
    }

    private String normalizeStatus(AgentToolResult result) {
        if (result.isRequiresAction()) {
            return AgentStepStatus.WAITING_USER.value();
        }
        return result.getStatus();
    }

    private String buildThought(AgentPlanStep step, AgentToolResult result, int attempt, int maxAttempts) {
        if (result.isRequiresAction()) {
            return "等待用户补充信息或确认: " + result.getMessage();
        }
        if (AgentStepStatus.SUCCESS.value().equals(result.getStatus())) {
            return "步骤 " + step.getId() + " 成功，可以继续后续依赖步骤。";
        }
        if (AgentStepStatus.ERROR.value().equals(result.getStatus())) {
            if (attempt < maxAttempts && !"none".equalsIgnoreCase(step.getRetryPolicy())) {
                return "步骤失败，将按 " + step.getRetryPolicy() + " 策略进行第 " + (attempt + 1) + " 次重试。";
            }
            return "步骤失败且已无重试余量，建议触发 replan 或返回错误。";
        }
        return "步骤进行中，等待结果。";
    }

    private void sleepBackoff(String policy, int attempt) {
        try {
            long base = "exponential".equalsIgnoreCase(policy) ? 200L * (1L << Math.min(attempt - 1, 4)) : 200L;
            Thread.sleep(Math.min(base, 2000L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private List<AgentPlanStep> parsePlan(String raw) throws Exception {
        if (isBlank(raw)) {
            return List.of();
        }
        String json = stripJson(raw);
        List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
        List<AgentPlanStep> steps = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> item : items) {
            AgentPlanStep step = new AgentPlanStep();
            step.setId(asString(item.getOrDefault("id", "step-" + index)));
            step.setDescription(asString(item.getOrDefault("description", item.getOrDefault("step", ""))));
            step.setToolName(asString(item.getOrDefault("toolName", item.getOrDefault("tool", "direct-answer"))));
            step.setReasoning(asString(item.get("reasoning")));
            step.setThought(asString(item.get("thought")));
            step.setSuccessCriteria(asString(item.get("successCriteria")));
            String retryPolicy = asString(item.get("retryPolicy"));
            if (!isBlank(retryPolicy)) {
                step.setRetryPolicy(retryPolicy);
            }
            Object maxRetries = item.get("maxRetries");
            if (maxRetries instanceof Number n) {
                step.setMaxRetries(n.intValue());
            }
            Object dependsOn = item.get("dependsOn");
            if (dependsOn instanceof List<?> list) {
                List<String> ids = new ArrayList<>();
                for (Object element : list) {
                    if (element != null) {
                        ids.add(element.toString());
                    }
                }
                step.setDependsOn(ids);
            }
            String riskLevel = asString(item.get("riskLevel"));
            if (!isBlank(riskLevel)) {
                step.setRiskLevel(riskLevel);
            }
            Object params = item.get("params");
            if (params instanceof Map<?, ?> map) {
                step.setParams(MapUtils.toStringObjectMap(map));
            }
            steps.add(step);
            index++;
        }
        return steps;
    }

    /**
     * 执行单个步骤一次：计时、状态流转（RUNNING）、结果修饰与状态归一化。
     * runStepWithRetry 与 retryStep 共用，避免重复的时序样板。
     */
    private AgentToolResult runStepOnce(AgentPlanStep step, AgentExecutionRequest request, Map<String, Object> context) {
        long startedAt = System.currentTimeMillis();
        step.setStatus(AgentStepStatus.RUNNING.value());
        AgentToolResult result = executeStep(step, request, context);
        decorateResult(result, step, startedAt);
        step.setStatus(normalizeStatus(result));
        step.setObservation(result.getMessage());
        return result;
    }

    private AgentToolResult executeStep(AgentPlanStep step, AgentExecutionRequest request, Map<String, Object> context) {
        try {
            placeholderResolver.resolve(step, request, context);
            AgentToolResult validationResult = validateToolParameters(step);
            if (validationResult != null) {
                return validationResult;
            }
            AgentToolResult approvalResult = enforceDestructiveApproval(step, context);
            if (approvalResult != null) {
                return approvalResult;
            }
            if (!internalService.hasPermission(step.getToolName(), context)) {
                return AgentToolResult.error(step.getToolName(), "没有执行此工具的权限或缺少服务端确认");
            }
            return toolRegistry.execute(step.getToolName(), step.getParams(), request, context);
        } catch (Exception e) {
            log.error("工具执行失败: tool={}, step={}", step.getToolName(), step.getDescription(), e);
            return AgentToolResult.error(step.getToolName(), e.getMessage());
        }
    }

    private AgentToolResult validateToolParameters(AgentPlanStep step) {
        AgentTool tool = toolRegistry.get(step.getToolName());
        if (tool == null) {
            return null;
        }
        AgentToolDefinition definition = tool.definition();

        Map<String, Object> params = step.getParams();
        if ("file-delete".equals(definition.getName())
                && isValueMissing(params.get("documentId"))
                && isValueMissing(params.get("objectName"))) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("toolName", definition.getName());
            data.put("missingParameters", List.of("documentId/objectName"));
            data.put("parameterSchema", definition.getParameterSchema());
            data.put("question", "工具 file-delete 需要提供前端documentId或MinIO对象名称objectName");
            data.put("nextStep", "请从前端上下文传入 documentId，或直接提供 bucketName/objectName 后重试。");
            data.put("interactionType", "user_input");
            data.put("resumeMode", "continue_trace");
            data.put("resumeEndpointTemplate", "/api/ai/agent/tasks/{traceId}/continue");
            return AgentToolResult.actionRequired(definition.getName(),
                    "请提供前端documentId或MinIO对象名称objectName", data);
        }
        List<String> missing = new ArrayList<>();
        Map<String, String> invalid = new LinkedHashMap<>();
        for (AgentToolParameterDefinition parameter : definition.getParameterSchema().values()) {
            Object value = params.get(parameter.getName());
            if (parameter.isRequired() && isValueMissing(value)) {
                missing.add(parameter.getName());
                continue;
            }
            if (isValueMissing(value)) {
                continue;
            }
            if (!isTypeCompatible(value, parameter.getType())) {
                invalid.put(parameter.getName(), "期望类型: " + parameter.getType());
                continue;
            }
            if (parameter.getAllowedValues() != null && !parameter.getAllowedValues().isEmpty()
                    && !parameter.getAllowedValues().contains(value)
                    && !parameter.getAllowedValues().contains(value.toString())) {
                invalid.put(parameter.getName(), "仅允许: " + parameter.getAllowedValues());
                continue;
            }
            if ("integer".equals(parameter.getType())) {
                long numeric;
                try {
                    numeric = Long.parseLong(value.toString());
                } catch (NumberFormatException ignore) {
                    invalid.put(parameter.getName(), "期望类型: integer");
                    continue;
                }
                if (parameter.getMinValue() != null && numeric < parameter.getMinValue().longValue()) {
                    invalid.put(parameter.getName(), "最小值: " + parameter.getMinValue());
                    continue;
                }
                if (parameter.getMaxValue() != null && numeric > parameter.getMaxValue().longValue()) {
                    invalid.put(parameter.getName(), "最大值: " + parameter.getMaxValue());
                    continue;
                }
            }
            if (parameter.getPattern() != null && !value.toString().matches(parameter.getPattern())) {
                invalid.put(parameter.getName(), "格式不匹配: " + parameter.getPattern());
            }
        }

        if (missing.isEmpty() && invalid.isEmpty()) {
            return null;
        }

        StringBuilder question = new StringBuilder("工具 ").append(definition.getName()).append(" 缺少必要信息: ");
        if (!missing.isEmpty()) {
            question.append("缺失参数 ").append(missing);
        }
        if (!invalid.isEmpty()) {
            if (!missing.isEmpty()) {
                question.append("; ");
            }
            question.append("非法参数 ").append(invalid.keySet());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", definition.getName());
        data.put("missingParameters", missing);
        data.put("invalidParameters", invalid);
        data.put("parameterSchema", definition.getParameterSchema());
        data.put("question", question.toString());
        data.put("nextStep", "请补齐缺失或类型不正确的参数后，将参数放入 context 或工具 params 中重试。");
        data.put("interactionType", "user_input");
        data.put("resumeMode", "continue_trace");
        data.put("resumeEndpointTemplate", "/api/ai/agent/tasks/{traceId}/continue");
        return AgentToolResult.actionRequired(definition.getName(), question.toString(), data);
    }

    private boolean isTypeCompatible(Object value, String type) {
        if (value == null) {
            return false;
        }
        return switch (valueOrDefault(type, "string")) {
            case "integer" -> value instanceof Number || value.toString().matches("^-?\\d+$");
            case "boolean" -> value instanceof Boolean
                    || "true".equalsIgnoreCase(value.toString())
                    || "false".equalsIgnoreCase(value.toString());
            default -> true;
        };
    }

    private boolean isValueMissing(Object value) {
        return value == null || value.toString().trim().isEmpty() || "null".equalsIgnoreCase(value.toString().trim());
    }

    private AgentToolResult enforceDestructiveApproval(AgentPlanStep step, Map<String, Object> context) {
        AgentTool tool = toolRegistry.get(step.getToolName());
        if (tool == null || !tool.definition().isDestructive()) {
            context.remove("confirmedAction");
            return null;
        }

        String userId = String.valueOf(context.get("userId"));
        String token = asString(step.getParams().get("agentApprovalToken"));
        AgentApprovalService.ApprovalResult result = agentApprovalService.verifyAndConsumeDetailed(token, userId, step.getToolName(), step.getParams());
        if (result == AgentApprovalService.ApprovalResult.OK) {
            context.put("confirmedAction", true);
            step.getParams().put("requireConfirmation", false);
            return null;
        }

        AgentApprovalService.ApprovalChallenge challenge =
                agentApprovalService.createChallenge(userId, step.getToolName(), step.getParams());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentApprovalToken", challenge.token());
        data.put("expiresAt", challenge.expiresAt());
        data.put("toolName", step.getToolName());
        data.put("params", step.getParams());
        data.put("approvalReason", result.name());
        data.put("nextStep", "确认危险操作后，将 agentApprovalToken 原样带回本接口重试。");
        data.put("interactionType", "approval");
        data.put("resumeMode", "approval_token");
        data.put("resumeEndpoint", "/api/ai/agent/approvals/confirm");
        String message = switch (result) {
            case EXPIRED_OR_USED -> "审批令牌已过期或已使用，请重新确认";
            case PARAMS_MISMATCH -> "审批令牌与当前参数不匹配，请重新确认";
            default -> "危险操作需要服务端二次确认";
        };
        return AgentToolResult.actionRequired(step.getToolName(), message, data);
    }

    private String synthesizeAnswer(AgentExecutionRequest request, List<AgentPlanStep> plan,
                                    List<AgentToolResult> results, Map<String, Object> context,
                                    boolean requiresAction, boolean simpleTask) {
        if (!results.isEmpty() && "direct-answer".equals(results.getLast().getToolName())) {
            Object answer = results.getLast().getData().get("answer");
            if (answer != null) {
                return answer.toString();
            }
        }
        if (simpleTask && !results.isEmpty()) {
            AgentToolResult last = results.getLast();
            if (AgentStepStatus.isSuccess(last.getStatus())) {
                String direct = firstNonBlank(
                        asString(last.getData().get("answer")),
                        asString(last.getData().get("result")),
                        asString(last.getData().get("summary")));
                if (!isBlank(direct)) {
                    return direct;
                }
            }
        }
        if (!results.isEmpty()) {
            AgentToolResult last = results.getLast();
            Object frontendWrite = last.getData().get("requiresFrontendWrite");
            if (Boolean.TRUE.equals(frontendWrite) || "true".equalsIgnoreCase(String.valueOf(frontendWrite))) {
                String changeLog = firstNonBlank(asString(last.getData().get("changeLog")), "AI内容已写入当前页面文档");
                return changeLog + "。内容已更新到当前前端文档，尚未保存到MinIO，请使用页面现有保存功能确认保存。";
            }
        }

        String prompt = """
                你是Dockit Agent，请根据任务、执行计划、工具结果给用户一个清晰的最终回复。
                如果需要用户补充文件、确认token或参数，请明确说明下一步。
                
                用户任务:
                %s
                
                执行计划:
                %s
                
                工具结果:
                %s
                
                上下文:
                %s
                
                是否需要用户动作: %s
                """.formatted(request.getTask(), safeJson(plan), safeJson(results), safeJson(context), requiresAction);
        try {
            return chatService.callChatApiWithModelCode(prompt, request.getModel());
        } catch (Exception e) {
            log.warn("最终回答生成失败，使用规则总结: {}", e.getMessage());
            return fallbackSummary(results, requiresAction);
        }
    }

    private String fallbackSummary(List<AgentToolResult> results, boolean requiresAction) {
        if (results.isEmpty()) {
            return "任务已规划，但没有执行任何工具。";
        }
        AgentToolResult last = results.getLast();
        if (requiresAction) {
            return last.getMessage();
        }
        return last.getMessage() + "：" + safeJson(last.getData());
    }

    private String resolveStatus(List<AgentToolResult> results) {
        if (results.isEmpty()) {
            return "planned";
        }
        return results.stream().anyMatch(r -> "error".equals(r.getStatus())) ? "error" : "success";
    }

    private String stripJson(String raw) {
        return stripFencedJson(raw, '[', ']');
    }

    private String stripObjectJson(String raw) {
        return stripFencedJson(raw, '{', '}');
    }

    /** 去掉 Markdown 代码围栏，并把首尾配对括号之间的内容截取出来。 */
    private String stripFencedJson(String raw, char open, char close) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf(open);
        int end = text.lastIndexOf(close);
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String safeJson(Object value) {
        return AgentToolSupport.safeJson(value);
    }

    private int intValue(Object value, int defaultValue) {
        return AgentToolSupport.intValue(value, defaultValue);
    }

    private boolean booleanValue(Object value) {
        return AgentToolSupport.booleanValue(value);
    }

    private String asString(Object value) {
        return AgentToolSupport.asString(value);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return AgentToolSupport.valueOrDefault(value, defaultValue);
    }

    private String firstNonBlank(String... values) {
        return AgentToolSupport.firstNonBlank(values);
    }

    private boolean isBlank(String value) {
        return AgentToolSupport.isBlank(value);
    }
}
