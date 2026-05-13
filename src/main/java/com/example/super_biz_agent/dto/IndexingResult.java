package com.example.super_biz_agent.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 向量索引进度与结果 — 由 {@code VectorIndexService.indexDirectory()} 返回。
 */
@Getter
public class IndexingResult {

    /** 整体是否成功 */
    @Setter
    private boolean success;

    /** 索引的目标目录路径 */
    @Setter
    private String directoryPath;

    /** 目录下待索引的文件总数 */
    @Setter
    private int totalFiles;

    /** 索引成功的文件数 */
    private int successCount;

    /** 索引失败的文件数 */
    private int failCount;

    /** 索引起始时间 */
    @Setter
    private LocalDateTime startTime;

    /** 索引结束时间 */
    @Setter
    private LocalDateTime endTime;

    /** 失败时的错误摘要 */
    @Setter
    private String errorMessage;

    /** 失败文件 → 错误原因的映射 */
    private final Map<String, String> failedFiles = new HashMap<>();

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public void incrementFailCount() {
        this.failCount++;
    }

    public void addFailedFile(String filePath, String error) {
        this.failedFiles.put(filePath, error);
    }

    /** 索引总耗时（毫秒） */
    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
}
