package com.javaee.documentservice.client;

import com.javaee.documentservice.client.dto.ContractCompareRequest;
import com.javaee.documentservice.client.dto.ContractCompareResponse;
import com.javaee.documentservice.client.dto.DocParserResponse;
import com.javaee.documentservice.client.dto.DocumentLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * doc-parser HTTP 客户端，调用 POST /api/v1/contracts/compare 做合同版本比对
 */
@Component
public class DocParserClient {

    private static final Logger log = LoggerFactory.getLogger(DocParserClient.class);
    private static final String COMPARE_PATH = "/api/v1/contracts/compare";

    private final RestClient restClient;

    public DocParserClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 调用 doc-parser 比对两个文档
     */
    public ContractCompareResponse compareContracts(DocumentLocator original, DocumentLocator modified) {
        ContractCompareRequest request = new ContractCompareRequest(original, modified);
        log.info("调用 doc-parser 比对: original={}, modified={}", original.fileUrl(), modified.fileUrl());
        try {
            DocParserResponse response = restClient.post()
                    .uri(COMPARE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(DocParserResponse.class);
            if (response == null || !response.success() || response.data() == null) {
                String message = response == null ? "空响应" : response.message();
                throw new DocParserException("doc-parser 比对失败: " + message);
            }
            return response.data();
        } catch (RestClientException e) {
            log.error("doc-parser 调用异常", e);
            throw new DocParserException("doc-parser 调用失败: " + e.getMessage(), e);
        }
    }
}
