package com.javaee.documentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * doc-parser 标准响应信封：{success, code, message, data, ...}
 * code 为字符串枚举（如 "OK"），非数字
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocParserResponse(
        boolean success,
        String code,
        String message,
        ContractCompareResponse data
) {
}
