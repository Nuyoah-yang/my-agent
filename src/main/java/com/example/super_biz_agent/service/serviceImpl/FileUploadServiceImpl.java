package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.config.FileUploadConfig;
import com.example.super_biz_agent.dto.FileUploadRes;
import com.example.super_biz_agent.service.FileUploadService;
import com.example.super_biz_agent.service.VectorIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static com.google.common.io.Files.getFileExtension;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {
    @Autowired
    private FileUploadConfig fileUploadConfig;

    @Autowired
    private VectorIndexService vectorIndexService;
    @Override
    public FileUploadRes upload(MultipartFile file) {
        if(file.isEmpty()){
            throw new IllegalArgumentException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if(originalFilename==null||originalFilename.isEmpty()){
            throw new IllegalArgumentException("文件名不能为空");
        }

        String fileExtension = getFileExtension(originalFilename);
        if(!isAllowedExtension(fileExtension)){
            throw new IllegalArgumentException("不支持的文件格式，仅支持: " + fileUploadConfig.getAllowedExtensions());
        }

        try {
            String uploadPath = fileUploadConfig.getPath();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            // 使用原始文件名，而不是UUID，以便实现基于文件名的去重
            Path filePath = uploadDir.resolve(originalFilename).normalize();

            // 如果文件已存在，先删除旧文件（实现覆盖更新）
            if (Files.exists(filePath)) {
                log.info("文件已存在，将覆盖: {}", filePath);
                Files.delete(filePath);
            }
            Files.copy(file.getInputStream(), filePath);
            log.info("文件上传成功: {}", filePath);

            // 文件上传成功后，异步触发向量索引（索引失败不影响上传结果）
            try {
                vectorIndexService.indexSingleFile(filePath.toString());
                log.info("向量索引已触发: {}", filePath);
            } catch (Exception e) {
                log.error("向量索引失败: {}, 错误: {}", filePath, e.getMessage(), e);
            }

            FileUploadRes response = new FileUploadRes(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );
            return response;

        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }

    }

    private boolean isAllowedExtension(String extension){
        String allowedExtensions = fileUploadConfig.getAllowedExtensions();
        if(allowedExtensions==null||allowedExtensions.isEmpty()){
            return false;
        }
        List<String> allowedList= Arrays.asList(allowedExtensions.split(","));
        return allowedList.contains(extension.toLowerCase());
    }
}
