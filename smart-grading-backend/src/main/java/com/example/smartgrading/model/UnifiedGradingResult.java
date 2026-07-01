package com.example.smartgrading.model;

import lombok.Data;
import java.util.List;

@Data
public class UnifiedGradingResult {
    private List<QuestionResult> questions;

    @Data
    public static class QuestionResult {
        private String studentAnswer;      // 识别出的学生手写答案
        private String result;              // "正确" 或 "错误"
        private String explanation;         // 解析
        private String errorAnalysis;       // 错因（仅错误时有值）
        private BoundingBox bbox;           // 坐标位置
    }

    @Data
    public static class BoundingBox {
        private int x;
        private int y;
        private int width;
        private int height;
    }
}
