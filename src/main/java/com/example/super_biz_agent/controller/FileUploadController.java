package com.example.super_biz_agent.controller;


import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.FileUploadRes;
import com.example.super_biz_agent.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;


    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ApiResponse<FileUploadRes> upload(@RequestParam("file")MultipartFile file){
        return ApiResponse.success(fileUploadService.upload(file));
    }

}
