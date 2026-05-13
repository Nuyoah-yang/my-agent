package com.example.super_biz_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadRes {
    //文件名
    private String fileName;
    //文件路径
    private String filePath;
    //文件大小
    private long size;
}
