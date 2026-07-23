package com.javaee.documentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 合同比对请求，对应 doc-parser 的 ContractCompareRequest（original + modified，extra=forbid）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractCompareRequest(DocumentLocator original, DocumentLocator modified) {
}
