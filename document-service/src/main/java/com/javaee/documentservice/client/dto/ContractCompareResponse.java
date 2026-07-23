package com.javaee.documentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 合同比对响应数据（doc-parser 响应信封的 data 部分）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractCompareResponse(
        @JsonProperty("summary_text") String summaryText,
        @JsonProperty("items") List<DiffItem> items,
        @JsonProperty("documents") Map<String, Object> documents
) {
}
