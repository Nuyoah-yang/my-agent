package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.IndexingResult;

/**
 * 向量索引服务 — 编排"文件 → 分片 → 向量化 → 写入 Milvus"全链路。
 * <p>
 * 主要入口为 {@link #indexSingleFile(String)}，供文件上传成功后调用。
 */
public interface VectorIndexService {

    /**
     * 索引单个文件：读取内容 → 删除旧数据 → 分片 → 向量化 → 逐片插入 Milvus。
     *
     * @param filePath 文件的绝对路径
     * @throws Exception 索引过程中任一环节失败均抛出异常
     */
    void indexSingleFile(String filePath) throws Exception;

    /**
     * 批量索引目录下所有 .txt / .md 文件。
     *
     * @param directoryPath 目录路径，为空时使用配置的上传目录
     * @return 索引进度与结果
     */
    IndexingResult indexDirectory(String directoryPath);
}
