package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.DocumentChunk;

import java.util.List;

/**
 * 文档分片服务 — 将长文档按语义边界切分为多个小片段。
 * <p>
 * 分片策略：优先按 Markdown 标题切分，再按段落边界细分，
 * 单个分片不超过配置的最大字符数，相邻分片之间保留重叠以维持上下文连贯性。
 */
public interface DocumentChunkService {

    /**
     * 对文档内容进行智能分片。
     *
     * @param content  文档原始内容
     * @param filePath 文件路径（用于日志追踪）
     * @return 分片列表，内容为空时返回空列表
     */
    List<DocumentChunk> chunkDocument(String content, String filePath);
}
