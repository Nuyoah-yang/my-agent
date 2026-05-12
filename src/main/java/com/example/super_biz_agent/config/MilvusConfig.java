package com.example.super_biz_agent.config;



import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.TimeUnit;


@Slf4j
@Configuration
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
public class MilvusConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private Integer port;

    @Value("${milvus.database}")
    private String database;

    @Value("${milvus.timeout}")
    private Long timeout;

    @Bean
    @Lazy
    public MilvusClient milvusClient() {
        log.info("Initializing Milvus client connecting to {}:{}", host, port);
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withDatabaseName(database)
                    .withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .withIdleTimeout(timeout, TimeUnit.MILLISECONDS)
                    .build();
            
            MilvusClient client = new MilvusServiceClient(connectParam);
            log.info("Milvus client initialized successfully");
            return client;
        } catch (Exception e) {
            log.error("Failed to create Milvus client. Please check if Milvus service is running at {}:{}", host, port, e);
            throw new RuntimeException("Failed to initialize Milvus client", e);
        }
    }
}
