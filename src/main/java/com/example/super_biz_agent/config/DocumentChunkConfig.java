package com.example.super_biz_agent.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档分片配置，绑定 application.yml 中的 document.chunk 前缀。
 */
@Getter
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {

    /** 每个分片的最大字符数，默认 800 */
    private int maxSize = 800;

    /** 相邻分片之间的重叠字符数，避免语义截断，默认 100 */
    private int overlap = 100;

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }
}
