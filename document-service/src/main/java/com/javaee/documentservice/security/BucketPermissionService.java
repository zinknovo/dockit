package com.javaee.documentservice.security;

import com.javaee.common.security.BucketPermissionChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 存储桶权限适配器：从 SecurityContextHolder 提取用户上下文，委托公共校验器
 * {@link BucketPermissionChecker}，校验逻辑只存在一份。
 */
@Service
public class BucketPermissionService {

    private final BucketPermissionChecker checker;

    @Autowired
    public BucketPermissionService(@Value("${minio.bucket-permissions:}") String bucketPermissions,
                                   @Value("${minio.bucket-permission-default-allow:false}") boolean defaultAllow) {
        this.checker = new BucketPermissionChecker(bucketPermissions, defaultAllow);
    }

    public void assertCanAccess(String bucketName) {
        Set<String> groups = currentPermissionGroups();
        checker.assertCanAccess(bucketName, groups.contains("admin"), groups, currentUserId());
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof String principal)) {
            return null;
        }
        return principal;
    }

    private Set<String> currentPermissionGroups() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && !authority.isBlank())
                .map(authority -> authority.replaceFirst("^ROLE_", "").replaceFirst("^GROUP_", ""))
                .map(authority -> authority.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
