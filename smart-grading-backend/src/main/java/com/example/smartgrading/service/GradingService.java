package com.example.smartgrading.service;

import com.example.smartgrading.model.GradeRequest;
import com.example.smartgrading.model.GradeResponse;
import com.example.smartgrading.model.UnifiedGradingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradingService {

    private final UnifiedGradingService unifiedGradingService;
    private final ImageMarkService imageMarkService;

    public GradeResponse processPaper(GradeRequest request) throws Exception {
        MultipartFile file = request.getFile();
        String standardAnswer = request.getStandardAnswer();

        // 1. 一次调用完成所有任务：识别 + 定位 + 判断 + 解析
        UnifiedGradingResult gradingResult = unifiedGradingService.gradePaperWithLocation(file, standardAnswer);

        // 2. 生成带批注的图片（根据模型返回的坐标和判断结果绘制）
        byte[] markedImage = imageMarkService.markImageWithUnifiedResult(file, gradingResult);

        // 保存到本地
        try (FileOutputStream fos = new FileOutputStream("marked_result.png")) {
            fos.write(markedImage);
            log.info("标记后图片已保存到项目根目录：marked_result.png");
        } catch (Exception e) {
            log.error("保存图片失败", e);
        }

        // 3. 组装响应
        GradeResponse response = new GradeResponse();
        response.setMarkedImageBase64(Base64.getEncoder().encodeToString(markedImage));

        // 取第一题的结果作为整体摘要（也可以根据需要汇总所有题目）
        if (gradingResult.getQuestions() != null && !gradingResult.getQuestions().isEmpty()) {
            UnifiedGradingResult.QuestionResult first = gradingResult.getQuestions().get(0);
            response.setResult(first.getResult());
            response.setExplanation(first.getExplanation());
            response.setErrorAnalysis(first.getErrorAnalysis());
        } else {
            response.setResult("未知");
            response.setExplanation("未识别到任何答案");
            response.setErrorAnalysis("");
        }
        return response;
    }
}
