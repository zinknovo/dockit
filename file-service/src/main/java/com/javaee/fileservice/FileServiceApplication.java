package com.javaee.fileservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.core.env.Environment;

/**
 * 文件服务应用启动类
 */
@SpringBootApplication(scanBasePackages = {"com.javaee.fileservice", "com.javaee.common"})
@EnableDiscoveryClient
@MapperScan(basePackages = "com.javaee.fileservice.mapper")
public class FileServiceApplication {
    private static final Logger log = LoggerFactory.getLogger(FileServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(FileServiceApplication.class);
        Environment environment = application.run(args).getEnvironment();
        log.info("Application name: " + environment.getProperty("spring.application.name"));
        log.info("Server port: " + environment.getProperty("server.port"));
    }

}
