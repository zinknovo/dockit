package com.javaee.aiservice.rag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：5xx 必须走指数退避重试（此前按消息子串 " 5" 判定，匹配不到 "500 Internal Server Error"）。
 */
class DocumentVectorizerTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);

    private DocumentVectorizer vectorizer() {
        DocumentVectorizer vectorizer = new DocumentVectorizer();
        ReflectionTestUtils.setField(vectorizer, "apiKey", "test-key");
        ReflectionTestUtils.setField(vectorizer, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(vectorizer, "model", "test-model");
        ReflectionTestUtils.setField(vectorizer, "restTemplate", restTemplate);
        return vectorizer;
    }

    @Test
    void serverErrorIsRetriedThreeTimesThenFails() {
        DocumentVectorizer vectorizer = vectorizer();
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> vectorizer.vectorize("文本"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("批量向量化失败");

        // 1 次原始调用 + 3 次重试（1s/2s/3s 退避）
        verify(restTemplate, times(4))
                .exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class));
    }

    @Test
    void clientErrorIsNotRetried() {
        DocumentVectorizer vectorizer = vectorizer();
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> vectorizer.vectorize("文本"))
                .isInstanceOf(RuntimeException.class);

        verify(restTemplate, times(1))
                .exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class));
    }

    @Test
    void recoversAfterTransientServerError() {
        DocumentVectorizer vectorizer = vectorizer();
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))
                .thenReturn(new ResponseEntity<>("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}", HttpStatus.OK));

        float[] embedding = vectorizer.vectorize("文本");

        assertThat(embedding).containsExactly(0.1f, 0.2f, 0.3f);
        verify(restTemplate, times(3))
                .exchange(any(String.class), any(HttpMethod.class), any(), eq(String.class));
    }
}
