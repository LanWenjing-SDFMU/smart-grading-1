package com.example.smartgrading.service;

import com.example.smartgrading.config.LlmProperties;
import com.example.smartgrading.model.UnifiedGradingResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedGradingService {

    private final LlmProperties llmProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UnifiedGradingResult gradePaperWithLocation(MultipartFile file, String standardAnswer) throws Exception {
        byte[] bytes = file.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String dataUrl = "data:image/png;base64," + base64;

        String prompt = String.format(
                "你是一位严谨的试卷批改老师。请分析这张试卷图片，完成以下任务：\n\n" +
                        "1. 识别出学生所有手写答案的位置，用边界框标出每个答案区域（坐标请返回像素值）。\n" +
                        "2. 识别每个答案区域内的手写文字内容。\n" +
                        "3. 将识别出的学生答案与标准答案进行比对。标准答案如下（每道题按顺序对应）：\n%s\n\n" +
                        "4. 对每个答案判断对错，并给出详细的解析和错因（如果错误的话）。\n\n" +
                        "请严格按照以下JSON格式返回结果，不要输出任何其他内容：\n" +
                        "{\n" +
                        "  \"questions\": [\n" +
                        "    {\n" +
                        "      \"studentAnswer\": \"识别出的学生手写答案文字\",\n" +
                        "      \"result\": \"正确\" 或 \"错误\",\n" +
                        "      \"explanation\": \"详细的解析过程\",\n" +
                        "      \"errorAnalysis\": \"错因分析（仅错误时填写，正确时留空字符串）\",\n" +
                        "      \"bbox\": {\n" +
                        "        \"x\": 左上角x坐标,\n" +
                        "        \"y\": 左上角y坐标,\n" +
                        "        \"width\": 宽度,\n" +
                        "        \"height\": 高度\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}",
                standardAnswer
        );

        // 构建 messages 格式（兼容 chat/completions）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmProperties.getModel());

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        // content 为数组，包含图片和文字
        List<Map<String, Object>> contentList = new ArrayList<>();
        contentList.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUrl)
        ));
        contentList.add(Map.of(
                "type", "text",
                "text", prompt
        ));
        userMessage.put("content", contentList);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        // 可选的温度参数
        requestBody.put("temperature", 0.1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 发送请求（使用配置的 endpoint，现在指向 /chat/completions）
        ResponseEntity<String> response = restTemplate.exchange(
                llmProperties.getEndpoint(),
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("豆包模型调用失败，响应码：{}，响应体：{}", response.getStatusCode(), response.getBody());
            throw new RuntimeException("模型调用失败，HTTP " + response.getStatusCode());
        }

        String responseBody = response.getBody();
        log.info("模型原始响应：{}", responseBody);

        // 解析 chat/completions 响应格式
        String contentText = extractContentFromChatResponse(responseBody);
        log.info("模型返回的原始文本（contentText）: {}", contentText);
        if (contentText == null || contentText.trim().isEmpty()) {
            throw new RuntimeException("模型未返回有效内容");
        }

        // 提取 JSON（去除 markdown 代码块等）
        String jsonStr = extractJson(contentText);
        log.info("提取到的 JSON：{}", jsonStr);

        return objectMapper.readValue(jsonStr, UnifiedGradingResult.class);
    }

    /**
     * 从 chat/completions 响应中提取 message.content
     */
    private String extractContentFromChatResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).path("message");
            return message.path("content").asText();
        }
        // 兼容其他格式
        if (root.has("output")) {
            return extractOutputText(responseBody);
        }
        return null;
    }

    /**
     * 备用：从 /responses 格式提取（以防万一）
     */
    private String extractOutputText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode node : output) {
                String type = node.path("type").asText();
                if ("message".equals(type)) {
                    JsonNode content = node.path("content");
                    if (content.isArray()) {
                        for (JsonNode item : content) {
                            if ("output_text".equals(item.path("type").asText())) {
                                return item.path("text").asText();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 从文本中提取 JSON（去除 markdown 代码块）
     */
    private String extractJson(String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }
}