package com.javaee.documentservice.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * doc-parser 比对服务配置
 */
@Configuration
@ConfigurationProperties(prefix = "dockit.doc-parser")
public class DocParserProperties {

    /** doc-parser 服务地址，本地 IDE 用 localhost，Docker 访问宿主用 host.docker.internal */
    private String baseUrl = "http://localhost:8080";

    /** 调用超时时间 */
    private Duration timeout = Duration.ofSeconds(60);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
