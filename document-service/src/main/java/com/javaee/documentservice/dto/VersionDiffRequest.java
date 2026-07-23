package com.javaee.documentservice.dto;

/**
 * 版本比对请求
 */
public record VersionDiffRequest(String fromVersionId, String toVersionId) {
}
