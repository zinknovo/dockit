package com.javaee.aiservice.agent.execution.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AgentToolDefinition 的构造工厂：按参数名推断类型 / 默认值 / 枚举白名单 / 数值范围。
 */
public final class ToolDefinitions {

    private ToolDefinitions() {
    }

    public static AgentToolDefinition create(String name, String description, Map<String, String> parameters,
                                             Set<String> requiredParameters, boolean destructive,
                                             String category, boolean requiresUserAction) {
        Map<String, AgentToolParameterDefinition> parameterSchema = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String parameterName = entry.getKey();
            parameterSchema.put(parameterName, new AgentToolParameterDefinition(
                    parameterName,
                    inferType(parameterName),
                    entry.getValue(),
                    requiredParameters.contains(parameterName),
                    defaultValue(parameterName),
                    allowedValues(parameterName),
                    minValue(parameterName),
                    maxValue(parameterName),
                    pattern(parameterName)
            ));
        }
        return new AgentToolDefinition(name, description, parameters, parameterSchema,
                destructive, category, requiresUserAction, destructive ? "high" : "low");
    }

    private static List<Object> allowedValues(String parameterName) {
        return switch (parameterName) {
            case "rerankStrategy" -> List.of("HYBRID", "VECTOR", "BM25");
            case "writeMode" -> List.of("overwrite", "append", "insert", "replace-selection");
            default -> null;
        };
    }

    private static Number minValue(String parameterName) {
        return switch (parameterName) {
            case "topK", "count" -> 1;
            case "maxLength" -> 50;
            default -> null;
        };
    }

    private static Number maxValue(String parameterName) {
        return switch (parameterName) {
            case "topK" -> 50;
            case "count" -> 50;
            case "maxLength" -> 5000;
            default -> null;
        };
    }

    private static String pattern(String parameterName) {
        return null;
    }

    private static String inferType(String parameterName) {
        if (Set.of("topK", "count", "maxLength").contains(parameterName)) {
            return "integer";
        }
        if (Set.of("requireConfirmation", "reindexAfterWrite").contains(parameterName)) {
            return "boolean";
        }
        return "string";
    }

    private static Object defaultValue(String parameterName) {
        return switch (parameterName) {
            case "topK" -> 5;
            case "count" -> 8;
            case "maxLength" -> 300;
            case "rerankStrategy" -> "HYBRID";
            case "requireConfirmation" -> true;
            case "writeMode" -> "append";
            case "contentType" -> "text/plain";
            case "reindexAfterWrite" -> true;
            default -> null;
        };
    }
}
