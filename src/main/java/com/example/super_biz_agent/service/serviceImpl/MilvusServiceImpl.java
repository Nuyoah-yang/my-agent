package com.example.super_biz_agent.service.serviceImpl;


import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.MilvusHealthData;
import com.example.super_biz_agent.service.MilvusService;
import io.milvus.client.MilvusClient;
import io.milvus.param.collection.ShowCollectionsParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class MilvusServiceImpl implements MilvusService {

    @Autowired(required = false)
    private MilvusClient milvusClient;
    @Override
    public ApiResponse getState() {

        MilvusHealthData data = new MilvusHealthData();

        try{
            //构建请求
            ShowCollectionsParam param = ShowCollectionsParam.newBuilder().build();
            //客户端调用
            milvusClient.showCollections(param);

            // 不抛异常 = 健康
            data.setHealthy(true);
            data.setCollections(List.of());

        } catch (Exception e) {
            // 异常 = 不健康
            data.setHealthy(false);
            data.setCollections(List.of());
        }

        return ApiResponse.success(data);
    }
}
