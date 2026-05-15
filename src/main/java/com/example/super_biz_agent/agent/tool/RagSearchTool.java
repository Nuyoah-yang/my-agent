package com.example.super_biz_agent.agent.tool;

import com.example.super_biz_agent.dto.SearchResult;
import com.example.super_biz_agent.service.VectorSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagSearchTool {

    private static final Logger logger = LoggerFactory.getLogger(RagSearchTool.class);

    public static final String TOOL_NAME = "queryInternalDocs";

    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${chat.rag.top-k:3}")
    private int topK;

    @Autowired
    public RagSearchTool(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @Tool(description = "Use this tool to search internal documentation and knowledge base for relevant information. "
            + "It performs RAG (Retrieval-Augmented Generation) to find similar documents. "
            + "This is useful when you need to understand internal procedures, best practices, or step-by-step guides "
            + "stored in the company's documentation.")
    public String queryInternalDocs(
            @ToolParam(description = "Search query describing what information you are looking for") String query) {

        logger.info("[RAG Tool] 收到查询请求: {}", query);

        try {
            List<SearchResult> results = vectorSearchService.searchSimilarDocuments(query, topK);

            if (results.isEmpty()) {
                return "{\"status\": \"no_results\", \"message\": \"No relevant documents found in the knowledge base.\"}";
            }

            String resultJson = objectMapper.writeValueAsString(results);
            logger.info("[RAG Tool] 查询完成, 命中 {} 条结果", results.size());
            return resultJson;

        } catch (Exception e) {
            logger.error("[RAG Tool] 查询失败", e);
            return String.format("{\"status\": \"error\", \"message\": \"Search failed: %s\"}", e.getMessage());
        }
    }
}
