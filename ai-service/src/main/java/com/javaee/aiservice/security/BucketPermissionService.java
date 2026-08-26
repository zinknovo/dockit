package com.javaee.aiservice.security;

import com.javaee.common.security.BucketPermissionChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 存储桶权限适配器：把 ai-service 的用户上下文（RequestUserContext）适配到公共校验器
 * {@link BucketPermissionChecker}，校验逻辑只存在一份。
 */
@Service
public class BucketPermissionService {

    private final BucketPermissionChecker checker;
    private final RequestUserContext requestUserContext;

    @Autowired
    public BucketPermissionService(@Value("${minio.bucket-permissions:}") String bucketPermissions,
                                   @Value("${minio.bucket-permission-default-allow:false}") boolean defaultAllow,
                                   RequestUserContext requestUserContext) {
        this.checker = new BucketPermissionChecker(bucketPermissions, defaultAllow);
        this.requestUserContext = requestUserContext;
    }

    public void assertCanAccess(String bucketName) {
        checker.assertCanAccess(bucketName, requestUserContext.isAdmin(),
                requestUserContext.getCurrentPermissionGroups(),
                requestUserContext.getCurrentUserId().orElse(null));
    }
}
