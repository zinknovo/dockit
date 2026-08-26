package com.javaee.aiservice.agent.execution;

import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.aiservice.agent.execution.model.AgentPlanStep;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 步骤参数模板占位符解析：${task}、${context.<key>}、${steps.<id>.<field>}。
 * 引用前序步骤结果时通过 context 中的 __stepResults__ 索引。
 */
public class PlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    public void resolve(AgentPlanStep step, AgentExecutionRequest request, Map<String, Object> context) {
        Map<String, Object> params = step.getParams();
        if (params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : new ArrayList<>(params.entrySet())) {
            Object value = entry.getValue();
            if (value instanceof String text && text.contains("${")) {
                String resolved = renderTemplate(text, request, context);
                params.put(entry.getKey(), resolved);
            }
        }
    }

    private String renderTemplate(String text, AgentExecutionRequest request, Map<String, Object> context) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            Object resolved = resolveExpression(expression, request, context);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolved == null ? "" : resolved.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @SuppressWarnings("unchecked")
    private Object resolveExpression(String expression, AgentExecutionRequest request, Map<String, Object> context) {
        if ("task".equals(expression)) {
            return request.getTask();
        }
        String[] parts = expression.split("\\.");
        if (parts.length < 2) {
            return context.get(expression);
        }
        String root = parts[0];
        if ("context".equals(root)) {
            return drillInto(context, parts, 1);
        }
        if ("steps".equals(root)) {
            Object steps = context.get("__stepResults__");
            if (!(steps instanceof Map)) {
                return null;
            }
            String stepId = parts[1];
            Object stepData = ((Map<String, Object>) steps).get(stepId);
            if (parts.length == 2) {
                return stepData;
            }
            if (stepData instanceof Map<?, ?> map) {
                return drillInto((Map<String, Object>) map, parts, 2);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object drillInto(Map<String, Object> source, String[] parts, int from) {
        Object current = source;
        for (int i = from; i < parts.length; i++) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(parts[i]);
        }
        return current;
    }
}
