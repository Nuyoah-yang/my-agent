package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.SearchResult;

import java.util.List;

/**
 * 向量检索服务 — 将查询文本向量化后在 Milvus 中检索最相似的分片。
 * <p>
 * 检索流程：查询文本 → Embedding 向量化 → Milvus L2 相似度搜索 → 返回 Top-K 分片。
 */
public interface VectorSearchService {

    /**
     * 在已索引的文档中检索与查询最相似的 K 个分片。
     *
     * @param query 用户查询文本
     * @param topK  返回的最相似结果数
     * @return 按相似度降序排列的分片列表（score 越小越相似）
     */
    List<SearchResult> searchSimilarDocuments(String query, int topK);
}
