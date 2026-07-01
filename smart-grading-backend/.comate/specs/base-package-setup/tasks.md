# 创建包结构和业务类（根据用户文档修订）

- [x] Task 1: 删除旧的 4 个 DTO 文件（OcrResult, LlmRequest, LlmResponse, GradingResult）
    - 1.1: 删除 OcrResult.java
    - 1.2: 删除 LlmRequest.java
    - 1.3: 删除 LlmResponse.java
    - 1.4: 删除 GradingResult.java

- [x] Task 2: 创建新的 DTO 类
    - 2.1: 创建 GradeRequest.java（file + studentAnswer + standardAnswer）
    - 2.2: 创建 GradeResponse.java（markedImageBase64 + result + comment）

- [x] Task 3: 创建 JudgeService.java（模拟评判逻辑）
    - 3.1: 实现 judge() 方法，判断学生答案是否包含标准答案关键字

- [x] Task 4: 创建 ImageMarkService.java（图像标记服务）
    - 4.1: 实现 markImage() 方法，在图片上绘制正确/错误标记并转为 byte[]
