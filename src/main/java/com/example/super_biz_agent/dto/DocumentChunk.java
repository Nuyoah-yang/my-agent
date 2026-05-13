package com.example.super_biz_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分片 — 一份长文档被切分后的单个片段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /** 分片文本内容 */
    private String content;

    /** 在原文档中的起始字符位置 */
    private int startIndex;

    /** 在原文档中的结束字符位置 */
    private int endIndex;

    /** 分片序号，从 0 开始递增 */
    private int chunkIndex;

    /** 所属的 Markdown 标题（可为 null） */
    private String title;

    public DocumentChunk(String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }
}
