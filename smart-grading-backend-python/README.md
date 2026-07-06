# 智能阅卷系统 - Python 后端

> 从 Java Spring Boot 项目迁移改造的 Python FastAPI 版本

## 技术栈

- **框架**: FastAPI (异步高性能 Web 框架)
- **图像处理**: Pillow (替代 Java AWT)
- **HTTP 客户端**: httpx (异步 HTTP，替代 HttpClient5)
- **数据验证**: Pydantic v2 (替代 Lombok + Jackson)
- **AI 模型**: 豆包大模型 / 火山引擎 API

## 项目结构

```
smart-grading-backend-python/
├── main.py                        # 应用入口（uvicorn）
├── requirements.txt               # Python 依赖
├── .env                           # 环境变量（API 密钥等）
├── .gitignore
├── README.md
└── app/
    ├── __init__.py
    ├── config.py                  # 配置（对应 LlmProperties + application.yml）
    ├── models.py                  # 数据模型（对应 GradeRequest/GradeResponse/UnifiedGradingResult）
    ├── exception.py               # 异常（对应 BusinessException）
    ├── routers/
    │   ├── __init__.py
    │   └── grading_router.py      # API 路由（对应 GradingController）
    └── services/
        ├── __init__.py
        ├── grading_service.py          # 业务编排（对应 GradingService）
        ├── unified_grading_service.py  # 大模型调用（对应 UnifiedGradingService）
        └── image_mark_service.py       # 图像标注（对应 ImageMarkService）
```

## 快速开始

### 1. 安装依赖

```bash
cd smart-grading-backend-python
pip install -r requirements.txt
```

### 2. 配置环境变量

编辑 `.env` 文件，配置大模型 API 密钥（已预设默认值，可直接使用）。

### 3. 启动服务

```bash
# 开发模式（热重载）
uvicorn main:app --reload --host 0.0.0.0 --port 8080

# 或直接运行
python main.py
```

### 4. 访问 API

- API 文档: http://localhost:8080/docs
- 健康检查: http://localhost:8080/health

## API 接口

### POST /api/grading/upload

上传试卷图片和标准答案进行批改。

**请求格式**: `multipart/form-data`

| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | 试卷图片 |
| standardAnswer | String | 标准答案 |

**响应**: 包含标注后图片（Base64）和批改结果的 JSON。

## 与 Java 版本的关键差异

| 特性 | Java 版本 | Python 版本 |
|------|-----------|------------|
| Web 框架 | Spring Boot 3.2.5 | FastAPI |
| 语言 | Java 17 | Python 3.10+ |
| 构建工具 | Maven | pip |
| HTTP 客户端 | HttpClient5 | httpx |
| 图像处理 | Java AWT / JavaCV | Pillow |
| JSON 处理 | Jackson | Pydantic |
| 异步 | 同步阻塞 | 异步 async/await |
| 配置 | application.yml + @ConfigurationProperties | .env + pydantic-settings |
| CORS | CorsConfig.java | FastAPI CORSMiddleware |
