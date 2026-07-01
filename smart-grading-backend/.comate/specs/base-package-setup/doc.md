# 基础包结构及业务类创建方案（根据用户文档修订）

## 1. 需求概述

在 `com.example.smartgrading` 下维护现有包结构（controller / service / model / config / util），并在 model 和 service 包中创建用户文档指定的类。

## 2. 架构与设计方案

### 2.1 包结构

```
com.example.smartgrading/
├── SmartGradingApplication.java   # Spring Boot 启动类（已创建，保持不变）
├── controller/                    # REST 控制器层（待后续填充）
├── service/
│   ├── JudgeService.java          # 模拟评判服务
│   └── ImageMarkService.java      # 图像标记服务
├── model/
│   ├── GradeRequest.java          # 接收前端参数（试卷图片 + 学生答案 + 标准答案）
│   └── GradeResponse.java         # 返回给前端（标记图片 base64 + 结果 + 评语）
├── config/                        # 配置类（待后续填充）
└── util/                          # 工具类（待后续填充）
```

### 2.2 关键变更说明

| 之前生成的 | 现在替换为 |
|-----------|-----------|
| `OcrResult.java` | **删除** |
| `LlmRequest.java` | **删除** |
| `LlmResponse.java` | **删除** |
| `GradingResult.java` | **删除** |
| (无) | **新增** `GradeRequest.java` |
| (无) | **新增** `GradeResponse.java` |
| (无) | **新增** `JudgeService.java` |
| (无) | **新增** `ImageMarkService.java` |

## 3. 涉及文件清单

| 文件路径 | 操作类型 |
|----------|----------|
| `model/OcrResult.java` | **删除** |
| `model/LlmRequest.java` | **删除** |
| `model/LlmResponse.java` | **删除** |
| `model/GradingResult.java` | **删除** |
| `model/GradeRequest.java` | **新增** |
| `model/GradeResponse.java` | **新增** |
| `service/JudgeService.java` | **新增** |
| `service/ImageMarkService.java` | **新增** |

## 4. 实现细节

### 4.1 GradeRequest.java

```java
package com.example.smartgrading.model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class GradeRequest {
    private MultipartFile file;          // 试卷图片
    private String studentAnswer;        // 学生答案（手写识别后文本，暂由用户输入）
    private String standardAnswer;       // 标准答案
}
```

### 4.2 GradeResponse.java

```java
package com.example.smartgrading.model;

import lombok.Data;

@Data
public class GradeResponse {
    private String markedImageBase64;    // 标记后的图片 Base64
    private String result;               // "正确" 或 "错误"
    private String comment;              // 评语（可选）
}
```

### 4.3 JudgeService.java

```java
package com.example.smartgrading.service;

import com.example.smartgrading.model.GradeRequest;
import org.springframework.stereotype.Service;

@Service
public class JudgeService {

    /**
     * 模拟评判逻辑：仅判断学生答案是否包含标准答案的关键字
     * （实际可替换为调用大模型 API）
     */
    public String judge(GradeRequest request) {
        String student = request.getStudentAnswer().trim();
        String standard = request.getStandardAnswer().trim();

        // 简单模拟：如果学生答案中包含了标准答案的所有字符（忽略空格），算正确
        boolean correct = student.replaceAll("\\s+", "")
                .contains(standard.replaceAll("\\s+", ""));
        return correct ? "正确" : "错误";
    }
}
```

### 4.4 ImageMarkService.java

```java
package com.example.smartgrading.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ImageMarkService {

    public byte[] markImage(MultipartFile file, String result) throws IOException {
        // 读取上传的图片
        BufferedImage image = ImageIO.read(file.getInputStream());

        // 创建 Graphics2D 用于绘图
        Graphics2D g = (Graphics2D) image.getGraphics();
        g.setFont(new Font("微软雅黑", Font.BOLD, 80));
        g.setStroke(new BasicStroke(5));

        // 根据结果绘制不同标记
        if ("正确".equals(result)) {
            g.setColor(Color.GREEN);
            g.drawString("✓ 正确", 50, 150);
        } else {
            g.setColor(Color.RED);
            g.drawString("✗ 错误", 50, 150);
        }

        g.dispose();

        // 将图片转为 byte 数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
```

## 5. 边界条件与异常处理

- `GradeRequest` 使用 `MultipartFile` 接收上传文件，Spring Boot 已自动配置 multipart 解析（`max-file-size: 20MB`）
- `GradeResponse` 仅使用 `@Data`，前端通过 JSON 接收
- `JudgeService.judge()` 中 `studentAnswer` 和 `standardAnswer` 均做了 `trim()`，避免前后空格影响判断
- `ImageMarkService.markImage()` 抛出 `IOException`，交由上层 Controller 处理
- 所有类均与用户文档一致，无额外设计

## 6. 数据流

```
前端上传 → Controller → JudgeService.judge()  →  "正确"/"错误"
                      → ImageMarkService.markImage()  →  byte[]
                      → GradeResponse (组装) → 前端 JSON
```

## 7. 期望结果

- 旧的 4 个 DTO 类被安全删除
- 新增 `GradeRequest`、`GradeResponse`、`JudgeService`、`ImageMarkService`
- 项目编译通过，无残留旧类引用
