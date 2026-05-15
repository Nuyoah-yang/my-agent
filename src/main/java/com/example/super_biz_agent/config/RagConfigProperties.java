package com.example.super_biz_agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chat.rag")
public class RagConfigProperties {

    /** Global RAG on/off switch. When false, agent is created without tools. */
    private boolean enabled = true;

    /** Number of top-k similar documents to retrieve from Milvus. */
    private int topK = 3;
}
