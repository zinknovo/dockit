package com.javaee.documentservice.versioning;

/**
 * 版本控制异常
 * 用于包装 git 命令执行失败、路径不合法等可控错误
 */
public class VersionControlException extends RuntimeException {

    public VersionControlException(String message) {
        super(message);
    }

    public VersionControlException(String message, Throwable cause) {
        super(message, cause);
    }
}
