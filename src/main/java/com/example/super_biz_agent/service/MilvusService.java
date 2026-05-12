package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.MilvusHealthData;

public interface MilvusService {
    /**
     * 返回 Milvus 健康状态。
     */
    ApiResponse<MilvusHealthData> getState();
}
