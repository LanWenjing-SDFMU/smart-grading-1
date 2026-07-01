package com.example.smartgrading.model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class GradeRequest {
    private MultipartFile file;          // 试卷图片
    private String standardAnswer;       // 标准答案
}
