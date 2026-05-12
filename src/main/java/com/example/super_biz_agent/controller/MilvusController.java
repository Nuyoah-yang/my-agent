package com.example.super_biz_agent.controller;


import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.MilvusHealthData;
import com.example.super_biz_agent.service.MilvusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/milvus")
public class MilvusController {

    @Autowired
    private MilvusService milvusService;

    @GetMapping("/health")
    public ApiResponse<MilvusHealthData> getState() {
        // 健康检查接口不抛业务异常，直接返回健康数据对象。
        return milvusService.getState();
    }
}
