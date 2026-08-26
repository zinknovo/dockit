package com.javaee.aiservice.security;

import com.javaee.common.security.BucketPermissionChecker;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 公共校验器（BucketPermissionChecker）的规则测试：权限组白名单、管理员直通、本人 bucket 直通。
 */
class BucketPermissionServiceTest {

    private static final String CONFIG = "documents=admin,user;doc-ai=admin,user";

    private BucketPermissionChecker checker() {
        return new BucketPermissionChecker(CONFIG, false);
    }

    @Test
    void userGroupCanAccessDocAiBucket() {
        assertThatCode(() -> checker().assertCanAccess("doc-ai", false, Set.of("user"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void groupCaseInsensitiveAndWhitespaceTolerant() {
        assertThatCode(() -> checker().assertCanAccess("DOC-AI", false, Set.of("User", "ROLE_USER"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void groupNotInWhitelistIsDenied() {
        assertThatThrownBy(() -> checker().assertCanAccess("doc-ai", false, Set.of("guest"), null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void adminBypassesWhitelist() {
        assertThatCode(() -> checker().assertCanAccess("secret-bucket", true, Set.of(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void userOwnBucketBypassesWhitelist() {
        assertThatCode(() -> checker().assertCanAccess("user-42", false, Set.of("guest"), "42"))
                .doesNotThrowAnyException();
    }

    @Test
    void blankBucketNameIsRejected() {
        assertThatThrownBy(() -> checker().assertCanAccess("  ", false, Set.of("user"), null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("存储桶名称不能为空");
    }

    @Test
    void wildcardGroupAllowsConfiguredBucket() {
        assertThat(checker().isAllowed("doc-ai", Set.of("*"))).isTrue();
        assertThat(checker().isAllowed("doc-ai", Set.of("guest"))).isFalse();
    }

    @Test
    void defaultAllowAppliesWhenNoConfig() {
        BucketPermissionChecker permissive = new BucketPermissionChecker("", true);
        assertThat(permissive.isAllowed("any-bucket", Set.of())).isTrue();
        BucketPermissionChecker strict = new BucketPermissionChecker("", false);
        assertThat(strict.isAllowed("any-bucket", Set.of())).isFalse();
    }
}
