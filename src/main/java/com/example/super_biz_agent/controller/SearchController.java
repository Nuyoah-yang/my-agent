package com.example.super_biz_agent.controller;

import com.example.super_biz_agent.config.RagConfigProperties;
import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.SearchRequest;
import com.example.super_biz_agent.dto.SearchResult;
import com.example.super_biz_agent.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class SearchController {

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private RagConfigProperties ragConfig;

    @PostMapping("/search")
    public ApiResponse<List<SearchResult>> search(@RequestBody SearchRequest request) {
        log.info("收到独立搜索请求, query: {}", request.getQuery());

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ApiResponse.error(400, "搜索查询不能为空");
        }

        int topK = (request.getTopK() != null && request.getTopK() > 0)
                ? request.getTopK()
                : ragConfig.getTopK();

        List<SearchResult> results = vectorSearchService.searchSimilarDocuments(
                request.getQuery().trim(), topK);

        return ApiResponse.success(results);
    }
}
