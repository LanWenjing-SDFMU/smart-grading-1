package com.example.smartgrading.model;

import lombok.Data;

@Data
public class GradeResponse {
    private String markedImageBase64;    // 标记后的图片 Base64
    private String result;               // "正确" 或 "错误"
    private String explanation;          // 解析过程
    private String errorAnalysis;        // 错因分析（仅错误时有值）
}
