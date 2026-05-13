package com.example.super_biz_agent.service;


import com.example.super_biz_agent.dto.FileUploadRes;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
    /**
     * 文件上传
     * @param file
     */
    FileUploadRes upload(MultipartFile file);

}
