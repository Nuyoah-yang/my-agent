package com.example.super_biz_agent.service.serviceImpl;


import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.MilvusHealthData;
import com.example.super_biz_agent.service.MilvusService;
import io.milvus.client.MilvusClient;
import io.milvus.param.collection.ShowCollectionsParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class MilvusServiceImpl implements MilvusService {

    @Autowired(required = false)
    private MilvusClient milvusClient;

    @Override
    public ApiResponse<MilvusHealthData> getState() {
        // 健康检查接口约定：无论 Milvus 是否可用，都返回 200 + 健康状态对象。
        MilvusHealthData data = new MilvusHealthData();

        try {
            // 若客户端未注入或调用异常，都会进入 catch 分支并返回 unhealthy。
            ShowCollectionsParam param = ShowCollectionsParam.newBuilder().build();
            milvusClient.showCollections(param);
            data.setHealthy(true);
            data.setCollections(List.of());
        } catch (Exception e) {
            data.setHealthy(false);
            data.setCollections(List.of());
        }

        return ApiResponse.success(data);
    }
}
