package com.javaee.documentservice.client;

import com.javaee.documentservice.client.dto.ContractCompareResponse;
import com.javaee.documentservice.client.dto.DocumentLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DocParserClient 单元测试，用 MockRestServiceServer mock doc-parser 响应
 */
class DocParserClientTest {

    private MockRestServiceServer server;
    private DocParserClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("http://localhost:8080").build();
        client = new DocParserClient(restClient);
    }

    @Test
    void compareContractsReturnsDataOnSuccess() {
        String body = """
                {"success":true,"code":"OK","message":"合同版本对比完成",
                 "data":{"summary_text":"共识别 1 处差异",
                         "items":[{"paragraph_diff_index":1,"original_paragraph_id":"1","modified_paragraph_id":"1",
                                   "diff_types":["修改"],"original_context":"旧内容","modified_context":"新内容",
                                   "baseline_positions":[{"content":"旧"}],"comparison_positions":[{"content":"新"}],
                                   "diff_count":1}],
                         "documents":{"original":{"file_name":"o.md"}}}}
                """;
        server.expect(requestTo("http://localhost:8080/api/v1/contracts/compare"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.original.file_url").value("http://minio/o"))
                .andExpect(jsonPath("$.modified.file_url").value("http://minio/m"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ContractCompareResponse response = client.compareContracts(
                new DocumentLocator("http://minio/o", "o1", "o.md", "md"),
                new DocumentLocator("http://minio/m", "m1", "m.md", "md"));

        assertThat(response.summaryText()).isEqualTo("共识别 1 处差异");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).paragraphDiffIndex()).isEqualTo(1);
        assertThat(response.items().get(0).originalContext()).isEqualTo("旧内容");
        assertThat(response.items().get(0).modifiedContext()).isEqualTo("新内容");
        assertThat(response.items().get(0).diffTypes()).containsExactly("修改");
        assertThat(response.items().get(0).baselinePositions()).hasSize(1);
    }

    @Test
    void compareContractsThrowsWhenSuccessFalse() {
        server.expect(requestTo("http://localhost:8080/api/v1/contracts/compare"))
                .andRespond(withSuccess(
                        "{\"success\":false,\"code\":\"DOCUMENT_COMPARE_FAILED\",\"message\":\"比对失败\",\"data\":null}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.compareContracts(
                new DocumentLocator("http://minio/o", null, "o.md", "md"),
                new DocumentLocator("http://minio/m", null, "m.md", "md")))
                .isInstanceOf(DocParserException.class)
                .hasMessageContaining("比对失败");
    }

    @Test
    void compareContractsThrowsOnHttpError() {
        server.expect(requestTo("http://localhost:8080/api/v1/contracts/compare"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.compareContracts(
                new DocumentLocator("http://minio/o", null, "o.md", "md"),
                new DocumentLocator("http://minio/m", null, "m.md", "md")))
                .isInstanceOf(DocParserException.class);
    }
}
