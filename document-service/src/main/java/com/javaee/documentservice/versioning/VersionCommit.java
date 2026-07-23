package com.javaee.documentservice.versioning;

import java.time.Instant;

/**
 * git 版本提交记录
 */
public record VersionCommit(String commitHash, Instant committedAt) {
}
