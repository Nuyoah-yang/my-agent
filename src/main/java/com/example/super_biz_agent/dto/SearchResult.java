package com.example.super_biz_agent.dto;

import lombok.Data;

/**
 * Milvus 向量检索结果 — 单条相似分片。
 */
@Data
public class SearchResult {

    /** Milvus 中的主键 ID */
    private String id;

    /** 匹配到的分片文本内容 */
    private String content;

    /** L2 距离分数，越小越相似 */
    private float score;

    /** 元数据 JSON 字符串（含 _source、chunkIndex 等） */
    private String metadata;
}
