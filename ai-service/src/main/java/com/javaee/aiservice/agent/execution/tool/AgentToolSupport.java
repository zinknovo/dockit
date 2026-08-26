package com.javaee.aiservice.agent.execution.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaee.aiservice.agent.execution.model.AgentExecutionRequest;
import com.javaee.common.utils.UserBucketUtils;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 共享于 Agent Tool 实现与 AgentExecutionService 的静态工具方法。
 * 单一事实源：执行器通过私有委托方法复用同一份实现。
 */
public final class AgentToolSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgentToolSupport() {
    }

    public static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String valueOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    public static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    public static String requireParam(Map<String, Object> params, String key) {
        String value = asString(params.get(key));
        if (isBlank(value)) {
            throw new IllegalArgumentException("缺少必要参数: " + key);
        }
        return value;
    }

    public static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return new java.util.HashMap<>();
        }
        return OBJECT_MAPPER.convertValue(value, new TypeReference<>() {
        });
    }

    public static String safeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public static boolean containsAny(String text, String... words) {
        if (text == null) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDeleteIntent(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return containsAny(text, "删除", "删掉", "移除", "清除")
                || lower.contains("delete")
                || lower.contains("remove");
    }

    /**
     * 从 context / request 推导用户 bucket 名；execute 入口保证 userId 已写入 request。
     */
    public static String defaultUserBucketName(AgentExecutionRequest request, Map<String, Object> context) {
        String userId = firstNonBlank(
                asString(context == null ? null : context.get("userId")),
                request == null ? null : request.getUserId());
        return bucketNameForUser(userId);
    }

    public static String bucketNameForUser(String userId) {
        return isBlank(userId) ? null : UserBucketUtils.bucketNameForUser(userId);
    }

    public static String extractQuotedText(String task) {
        if (task == null) {
            return null;
        }
        int left = Math.max(task.lastIndexOf('“'), task.lastIndexOf('"'));
        int right = Math.max(task.lastIndexOf('”'), task.lastIndexOf('"'));
        if (left >= 0 && right > left) {
            return task.substring(left + 1, right);
        }
        return null;
    }

    public static String extractObjectNameFromTask(String task) {
        if (task == null) {
            return null;
        }
        String normalized = task.trim().replaceAll("\\s+\\.", ".").replaceAll("\\.\\s+", ".");
        String extensionPattern = "[^\\s，。；;、]+\\.[A-Za-z0-9]{1,8}";
        String keywordPattern = "(?:objectName|对象名|文件名|删除文件|下载文件|恢复文件|切换文件|文件|删除|下载|版本|恢复)[为是:：\\s]*(" + extensionPattern + ")";
        Matcher keywordMatcher = Pattern.compile(keywordPattern, Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (keywordMatcher.find()) {
            return stripObjectNamePunctuation(keywordMatcher.group(1));
        }
        Matcher matcher = Pattern.compile("(" + extensionPattern + ")", Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        return matcher.find() ? stripObjectNamePunctuation(matcher.group(1)) : null;
    }

    private static String stripObjectNamePunctuation(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        while (!result.isEmpty() && "，。；;、,.\"'“”‘’）)]}".indexOf(result.charAt(result.length() - 1)) >= 0) {
            result = result.substring(0, result.length() - 1).trim();
        }
        while (!result.isEmpty() && "\"'“”‘’（([{".indexOf(result.charAt(0)) >= 0) {
            result = result.substring(1).trim();
        }
        return isBlank(result) ? null : result;
    }

}
