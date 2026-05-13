package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.config.MilvusConstants;
import com.example.super_biz_agent.dto.SearchResult;
import com.example.super_biz_agent.service.VectorEmbeddingService;
import com.example.super_biz_agent.service.VectorSearchService;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 向量检索实现 — 查询向量化 → Milvus L2 搜索 → 解析结果。
 * <p>
 * 使用 IVF_FLAT 索引 + L2 欧氏距离，nprobe=10 在精度和速度间取平衡。
 */
@Slf4j
@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    @Autowired
    private MilvusClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    /** Milvus 搜索时额外返回的标量字段 */
    private static final List<String> OUT_FIELDS = List.of("id", "content", "metadata");

    @Override
    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        log.info("开始向量检索, 查询: {}, topK: {}", query, topK);

        try {
            // 1. 查询文本 → 1024 维向量
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            log.debug("查询向量生成成功, 维度: {}", queryVector.size());

            // 2. 构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.COLLECTION_NAME)
                    .withVectorFieldName("vector")                         // 向量字段名
                    .withVectors(Collections.singletonList(queryVector))   // 查询向量
                    .withTopK(topK)                                        // 返回 Top-K
                    .withMetricType(MetricType.L2)                         // L2 欧氏距离
                    .withOutFields(OUT_FIELDS)                             // 返回的标量字段
                    .withParams("{\"nprobe\":10}")                         // 搜索时探测的聚类数
                    .build();

            // 3. 执行搜索
            R<SearchResults> response = milvusClient.search(searchParam);

            if (response.getStatus() != 0) {
                throw new RuntimeException("向量搜索失败: " + response.getMessage());
            }

            // 4. 解析搜索结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResult result = new SearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());

                // metadata 字段为 JSON 对象，toString 后存入
                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }

                results.add(result);
            }

            log.info("向量检索完成, 命中 {} 条", results.size());
            return results;

        } catch (Exception e) {
            log.error("向量检索失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }
}
