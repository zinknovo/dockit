package com.javaee.documentservice.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * doc-parser RestClient 配置：构建带超时的 RestClient Bean
 */
@Configuration
public class DocParserClientConfig {

    @Bean
    public RestClient docParserRestClient(DocParserProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) properties.getTimeout().toMillis();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return builder.baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }
}
