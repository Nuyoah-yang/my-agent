package com.example.super_biz_agent.service;

import java.util.List;

/**
 * 向量嵌入服务 — 将文本转为向量表示，供 Milvus 检索和入库使用。
 * <p>
 * 底层调用 DashScope Text Embedding API（模型: text-embedding-v4），
 * 返回 1024 维浮点向量。
 */
public interface VectorEmbeddingService {

    /**
     * 将单段文本转为向量。
     *
     * @param content 原始文本（不能为空）
     * @return 1024 维浮点向量
     * @throws IllegalArgumentException 文本为空时抛出
     */
    List<Float> generateEmbedding(String content);

    /**
     * 批量文本向量化，一次 API 调用处理多条文本，节省网络开销。
     * <p>
     * 适用场景：文档分片后批量入库。
     *
     * @param contents 文本列表（可为空列表）
     * @return 与输入一一对应的向量列表，输入为空时返回空列表
     */
    List<List<Float>> generateEmbeddings(List<String> contents);

    /**
     * 将用户查询文本转为向量，用于 Milvus 相似度检索。
     * <p>
     * 等价于 {@link #generateEmbedding(String)}，语义上区分"入库"和"查询"两种场景。
     *
     * @param query 用户查询文本
     * @return 1024 维浮点向量
     */
    List<Float> generateQueryVector(String query);
}
