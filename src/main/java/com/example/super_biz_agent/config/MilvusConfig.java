package com.example.super_biz_agent.config;



import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;


@Configuration
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
    public MilvusClient milvusClient() {
        return new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(host)
                        .withPort(port)
                        .withDatabaseName(database)
                        .withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
                        .withIdleTimeout(timeout,TimeUnit.MILLISECONDS)
                        .build()
        );
    }
}
