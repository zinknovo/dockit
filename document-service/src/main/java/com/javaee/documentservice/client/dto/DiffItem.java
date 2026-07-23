package com.javaee.documentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 段落级差异项，对应 doc-parser items[] 中的元素
 * 位置信息用 List&lt;Map&gt; 透传，不在 Java 侧穷举字段
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiffItem(
        @JsonProperty("paragraph_diff_index") Integer paragraphDiffIndex,
        @JsonProperty("original_paragraph_id") String originalParagraphId,
        @JsonProperty("modified_paragraph_id") String modifiedParagraphId,
        @JsonProperty("diff_types") List<String> diffTypes,
        @JsonProperty("original_context") String originalContext,
        @JsonProperty("modified_context") String modifiedContext,
        @JsonProperty("baseline_positions") List<Map<String, Object>> baselinePositions,
        @JsonProperty("comparison_positions") List<Map<String, Object>> comparisonPositions,
        @JsonProperty("diff_count") Integer diffCount
) {
}
