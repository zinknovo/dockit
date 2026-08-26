package com.javaee.common.security;

import com.javaee.common.utils.UserBucketUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MinIO 存储桶权限校验（纯逻辑，无用户上下文依赖）。
 * 用户上下文（是否管理员 / 权限组 / userId）由各服务的薄适配器提取后传入，
 * 避免 file / ai / document 三个服务各自复制一份校验实现。
 */
public final class BucketPermissionChecker {

    private final String bucketPermissions;
    private final boolean defaultAllow;

    public BucketPermissionChecker(String bucketPermissions, boolean defaultAllow) {
        this.bucketPermissions = bucketPermissions == null ? "" : bucketPermissions;
        this.defaultAllow = defaultAllow;
    }

    /**
     * 校验当前用户能否访问存储桶。
     *
     * @param bucketName 存储桶名称
     * @param admin      是否管理员（管理员直接放行）
     * @param userGroups 用户权限组（已按服务上下文归一化的原始集合）
     * @param userId     当前用户ID，用于"本人 bucket"直通，未知时传 null
     */
    public void assertCanAccess(String bucketName, boolean admin, Set<String> userGroups, String userId) {
        if (admin) {
            return;
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new SecurityException("存储桶名称不能为空");
        }
        if (userId != null && UserBucketUtils.isUserBucket(bucketName, userId)) {
            return;
        }
        if (isAllowed(bucketName, userGroups)) {
            return;
        }
        throw new SecurityException("无权访问存储桶: " + bucketName);
    }

    public boolean isAllowed(String bucketName, Set<String> userGroups) {
        if (bucketPermissions.isBlank()) {
            return defaultAllow;
        }
        Set<String> allowedGroups = parseGroups(permissionsForBucket(bucketName));
        if (allowedGroups.isEmpty()) {
            return defaultAllow;
        }
        Set<String> normalizedUserGroups = normalizeGroups(userGroups);
        return normalizedUserGroups.contains("*") || allowedGroups.contains("*")
                || allowedGroups.stream().anyMatch(normalizedUserGroups::contains);
    }

    private Set<String> parseGroups(String groups) {
        if (groups == null || groups.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(groups.split("[,;]"))
                .map(this::normalizeGroup)
                .filter(group -> !group.isBlank())
                .collect(Collectors.toSet());
    }

    private String permissionsForBucket(String bucketName) {
        String normalizedBucket = normalizeGroup(bucketName);
        for (String entry : bucketPermissions.split("[;\\n]")) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && normalizedBucket.equals(normalizeGroup(parts[0]))) {
                return parts[1];
            }
        }
        return "";
    }

    private Set<String> normalizeGroups(Set<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String group : groups) {
            String value = normalizeGroup(group);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeGroup(String group) {
        return group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
    }
}
