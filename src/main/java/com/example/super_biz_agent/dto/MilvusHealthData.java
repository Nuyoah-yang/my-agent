package com.example.super_biz_agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class MilvusHealthData {
    boolean healthy;
    List<String> collections;
}
