package com.javaee.documentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文档定位信息，对应 doc-parser 的 DocumentRequest（snake_case 字段）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentLocator(
        @JsonProperty("file_url") String fileUrl,
        @JsonProperty("cache_key") String cacheKey,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_type") String fileType) {
}
