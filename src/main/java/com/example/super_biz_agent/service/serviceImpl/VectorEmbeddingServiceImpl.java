package com.example.super_biz_agent.service.serviceImpl;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.example.super_biz_agent.service.VectorEmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class VectorEmbeddingServiceImpl implements VectorEmbeddingService {

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.embedding.model}")
    private String model;

    private TextEmbedding textEmbedding;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("请设置环境变量 DASHSCOPE_API_KEY 或在 application.yml 中配置 API Key");
        }

        String maskedKey = apiKey.length() > 8
                ? apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4)
                : "***";
        log.info("DashScope API Key 已加载: {}", maskedKey);

        Constants.apiKey = apiKey;
        textEmbedding = new TextEmbedding();
        log.info("DashScope Embedding 服务初始化完成，模型: {}", model);
    }

    @Override
    public List<Float> generateEmbedding(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("内容不能为空");
        }

        try {
            if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
                Constants.apiKey = apiKey;
            }

            TextEmbeddingParam param = buildParam(Collections.singletonList(content));

            TextEmbeddingResult result = textEmbedding.call(param);

            if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new RuntimeException("DashScope API 返回空结果");
            }

            List<TextEmbeddingResultItem> embeddings = result.getOutput().getEmbeddings();
            if (embeddings.isEmpty()) {
                throw new RuntimeException("DashScope API 返回空向量列表");
            }

            List<Double> embeddingDoubles = embeddings.get(0).getEmbedding();
            List<Float> floatEmbedding = new ArrayList<>(embeddingDoubles.size());
            for (Double value : embeddingDoubles) {
                floatEmbedding.add(value.floatValue());
            }

            log.info("向量生成成功, 内容长度: {} 字符, 向量维度: {}", content.length(), floatEmbedding.size());
            return floatEmbedding;

        } catch (NoApiKeyException e) {
            log.error("API Key 无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            log.error("生成向量嵌入失败", e);
            throw new RuntimeException("生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<Float>> generateEmbeddings(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
                Constants.apiKey = apiKey;
            }

            TextEmbeddingParam param = buildParam(contents);

            TextEmbeddingResult result = textEmbedding.call(param);

            if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new RuntimeException("批量 DashScope API 返回空结果");
            }

            List<TextEmbeddingResultItem> embeddingItems = result.getOutput().getEmbeddings();
            if (embeddingItems.isEmpty()) {
                throw new RuntimeException("批量 DashScope API 返回空向量列表");
            }

            List<List<Float>> embeddings = new ArrayList<>();
            for (TextEmbeddingResultItem item : embeddingItems) {
                List<Double> embeddingDoubles = item.getEmbedding();
                List<Float> embedding = new ArrayList<>(embeddingDoubles.size());
                for (Double value : embeddingDoubles) {
                    embedding.add(value.floatValue());
                }
                embeddings.add(embedding);
            }

            log.info("批量向量生成成功, 数量: {}, 维度: {}",
                    embeddings.size(), embeddings.isEmpty() ? 0 : embeddings.get(0).size());
            return embeddings;

        } catch (NoApiKeyException e) {
            log.error("批量调用时 API Key 无效", e);
            throw new RuntimeException("API Key 未设置", e);
        } catch (Exception e) {
            log.error("批量生成向量嵌入失败", e);
            throw new RuntimeException("批量生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Float> generateQueryVector(String query) {
        return generateEmbedding(query);
    }

    private TextEmbeddingParam buildParam(List<String> texts) {
        return TextEmbeddingParam.builder()
                .model(model)
                .texts(texts)
                .build();
    }
}
