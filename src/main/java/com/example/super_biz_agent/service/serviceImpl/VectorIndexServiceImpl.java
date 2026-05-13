package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.config.MilvusConstants;
import com.example.super_biz_agent.dto.DocumentChunk;
import com.example.super_biz_agent.dto.IndexingResult;
import com.example.super_biz_agent.service.DocumentChunkService;
import com.example.super_biz_agent.service.VectorEmbeddingService;
import com.example.super_biz_agent.service.VectorIndexService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 向量索引实现 — 编排"读取 → 分片 → 向量化 → 写入 Milvus"全流程。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>读取文件内容（UTF-8）</li>
 *   <li>按 {@code _source} 删除该文件在 Milvus 中的旧数据（覆盖更新）</li>
 *   <li>调用 {@link DocumentChunkService} 切分文档</li>
 *   <li>逐片调用 {@link VectorEmbeddingService} 生成 1024 维向量</li>
 *   <li>将向量、文本、元数据写入 Milvus {@code biz} 集合</li>
 * </ol>
 */
@Slf4j
@Service
public class VectorIndexServiceImpl implements VectorIndexService {

    @Autowired
    private MilvusClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired
    private DocumentChunkService chunkService;

    @Value("${file.upload.path}")
    private String uploadPath;

    /** JSON 序列化工具（Milvus metadata 字段需要 Gson JsonObject） */
    private static final Gson GSON = new Gson();

    // ======================== 单文件索引 ========================

    @Override
    public void indexSingleFile(String filePath) throws Exception {
        Path path = Paths.get(filePath).normalize();
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        log.info("开始索引文件: {}", path);

        // 1. 读取文件内容
        String content = Files.readString(path);
        log.info("读取文件: {}, 内容长度: {} 字符", path, content.length());

        // 2. 删除该文件的旧向量数据（覆盖更新场景）
        deleteBySource(filePath);

        // 3. 文档分片
        List<DocumentChunk> chunks = chunkService.chunkDocument(content, filePath);
        log.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());

        // 4. 逐片生成向量并插入 Milvus
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            try {
                // 4a. 向量化
                List<Float> vector = embeddingService.generateEmbedding(chunk.getContent());

                // 4b. 构建元数据（来源文件、分片序号等）
                Map<String, Object> metadata = buildMetadata(filePath, chunk, chunks.size());

                // 4c. 插入 Milvus
                insertToMilvus(chunk.getContent(), vector, metadata, chunk.getChunkIndex());

                log.info("分片 {}/{} 索引成功", i + 1, chunks.size());
            } catch (Exception e) {
                log.error("分片 {}/{} 索引失败", i + 1, chunks.size(), e);
                throw new RuntimeException("分片索引失败: " + e.getMessage(), e);
            }
        }

        log.info("文件索引完成: {}, 共 {} 个分片", filePath, chunks.size());
    }

    // ======================== 批量目录索引 ========================

    @Override
    public IndexingResult indexDirectory(String directoryPath) {
        IndexingResult result = new IndexingResult();
        result.setStartTime(LocalDateTime.now());

        try {
            String targetPath = (directoryPath != null && !directoryPath.trim().isEmpty())
                    ? directoryPath : uploadPath;

            Path dirPath = Paths.get(targetPath).normalize();
            File directory = dirPath.toFile();

            if (!directory.exists() || !directory.isDirectory()) {
                throw new IllegalArgumentException("目录不存在或不是有效目录: " + targetPath);
            }

            result.setDirectoryPath(directory.getAbsolutePath());

            // 过滤出 .txt 和 .md 文件
            File[] files = directory.listFiles((dir, name) ->
                    name.endsWith(".txt") || name.endsWith(".md")
            );

            if (files == null || files.length == 0) {
                log.warn("目录中没有找到支持的文件: {}", targetPath);
                result.setTotalFiles(0);
                result.setSuccess(true);
                result.setEndTime(LocalDateTime.now());
                return result;
            }

            result.setTotalFiles(files.length);
            log.info("开始索引目录: {}, 共 {} 个文件", targetPath, files.length);

            for (File file : files) {
                try {
                    indexSingleFile(file.getAbsolutePath());
                    result.incrementSuccessCount();
                } catch (Exception e) {
                    result.incrementFailCount();
                    result.addFailedFile(file.getAbsolutePath(), e.getMessage());
                    log.error("文件索引失败: {}", file.getName(), e);
                }
            }

            result.setSuccess(result.getFailCount() == 0);
            result.setEndTime(LocalDateTime.now());

            log.info("目录索引完成: 总数={}, 成功={}, 失败={}",
                    result.getTotalFiles(), result.getSuccessCount(), result.getFailCount());
            return result;

        } catch (Exception e) {
            log.error("索引目录失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            return result;
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 删除指定文件在 Milvus 中的全部旧向量数据。
     * <p>
     * 通过 metadata 中的 {@code _source} 字段匹配，路径统一使用正斜杠
     * 以避免 Milvus 表达式解析时的转义问题。
     */
    private void deleteBySource(String filePath) {
        try {
            // 统一为正斜杠路径，避免 Windows 反斜杠在 Milvus 表达式中的转义问题
            String normalizedPath = Paths.get(filePath).normalize()
                    .toString().replace(File.separator, "/");

            // Milvus 标量过滤表达式：metadata["_source"] == "xxx"
            String expr = String.format("metadata[\"_source\"] == \"%s\"", normalizedPath);
            log.info("准备删除旧数据, 路径: {}, 表达式: {}", normalizedPath, expr);

            // 删除前确保 collection 已加载到内存
            ensureCollectionLoaded();

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            R<MutationResult> response = milvusClient.delete(deleteParam);

            if (response.getStatus() == 0) {
                long deletedCount = response.getData().getDeleteCnt();
                log.info("已删除旧数据: {}, 删除记录数: {}", normalizedPath, deletedCount);
            } else {
                log.warn("删除旧数据时返回非零状态: {}", response.getMessage());
            }
        } catch (Exception e) {
            // 首次索引时该文件无旧数据，属于正常情况
            log.warn("删除旧数据失败（可能为首次索引）: {}", e.getMessage());
        }
    }

    
    /**
     * 构建分片元数据，存入 Milvus 的 JSON 字段。
     * <p>
     * 包含文件来源信息（_source, _file_name, _extension）和
     * 分片位置信息（chunkIndex, totalChunks, title）。
     */
    private Map<String, Object> buildMetadata(String filePath, DocumentChunk chunk, int totalChunks) {
        Map<String, Object> metadata = new HashMap<>();
        Path path = Paths.get(filePath).normalize();

        // 文件来源信息（路径统一正斜杠）
        metadata.put("_source", path.toString().replace(File.separator, "/"));

        String fileNameStr = path.getFileName() != null ? path.getFileName().toString() : "";
        metadata.put("_file_name", fileNameStr);

        int dotIndex = fileNameStr.lastIndexOf('.');
        metadata.put("_extension", dotIndex > 0 ? fileNameStr.substring(dotIndex) : "");

        // 分片位置信息
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("totalChunks", totalChunks);
        if (chunk.getTitle() != null && !chunk.getTitle().isEmpty()) {
            metadata.put("title", chunk.getTitle());
        }

        return metadata;
    }

    /**
     * 将单条向量记录插入 Milvus。
     * <p>
     * 写入四个字段：id（UUID）、content（原文）、vector（1024 维浮点向量）、
     * metadata（Gson JsonObject）。
     */
    private void insertToMilvus(String content, List<Float> vector,
                                Map<String, Object> metadata, int chunkIndex) {
        // 插入前确保 collection 已加载
        ensureCollectionLoaded();

        // 生成确定性的 UUID：相同 _source + chunkIndex → 相同 ID，支持幂等覆盖
        String source = (String) metadata.get("_source");
        String id = UUID.nameUUIDFromBytes((source + "_" + chunkIndex).getBytes()).toString();

        // 将 metadata Map 转为 Gson JsonObject（Milvus JSON 字段的要求）
        JsonObject metadataJson = GSON.toJsonTree(metadata).getAsJsonObject();

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", Collections.singletonList(id)));
        fields.add(new InsertParam.Field("content", Collections.singletonList(content)));
        fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));
        fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MilvusConstants.COLLECTION_NAME)
                .withFields(fields)
                .build();

        R<MutationResult> response = milvusClient.insert(insertParam);

        if (response.getStatus() != 0) {
            throw new RuntimeException("插入向量失败: " + response.getMessage());
        }

        log.debug("向量插入成功: id={}, source={}, chunk={}", id, source, chunkIndex);
    }

    /**
     * 确保 {@code biz} collection 已加载到内存。
     * <p>
     * 状态码 65535 表示集合已经加载，不视为错误。
     */
    private void ensureCollectionLoaded() {
        R<RpcStatus> response = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(MilvusConstants.COLLECTION_NAME)
                        .build()
        );

        if (response.getStatus() != 0 && response.getStatus() != 65535) {
            throw new RuntimeException("加载 collection 失败: " + response.getMessage());
        }
    }
}
