"""
大模型调用服务，对应 Java 的 UnifiedGradingService

功能：
1. 读取并压缩试卷图片
2. 构造 Prompt，调用大模型 API（兼容 OpenAI Chat Completions 格式）
3. 解析模型返回的 JSON 结果
4. 检查是否因长度截断
"""

import base64
import json
import logging
import re
from io import BytesIO

import httpx
from PIL import Image

from app.config import settings
from app.models import UnifiedGradingResult

logger = logging.getLogger(__name__)


class UnifiedGradingService:
    """统一批改服务：调用大模型识别 + 批改试卷"""

    def __init__(self):
        self.api_key = settings.ai_llm_api_key
        self.model = settings.ai_llm_model
        self.endpoint = settings.ai_llm_endpoint

    async def grade_paper_with_location(
        self, image_bytes: bytes, standard_answer: str
    ) -> UnifiedGradingResult:
        """
        调用大模型批改试卷

        Args:
            image_bytes: 原始图片字节
            standard_answer: 标准答案文本

        Returns:
            UnifiedGradingResult: 批改结果
        """
        # 1. 读取并压缩图片
        compressed_bytes = self._compress_image(image_bytes, max_size=1200)
        base64_str = base64.b64encode(compressed_bytes).decode("utf-8")
        data_url = f"data:image/png;base64,{base64_str}"

        # 2. 构造 Prompt
        prompt_text = self._build_prompt(standard_answer)

        # 3. 构造请求体（OpenAI Chat Completions 格式）
        request_body = {
            "model": self.model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": data_url}},
                        {"type": "text", "text": prompt_text},
                    ],
                }
            ],
            "temperature": 0.1,
            "max_tokens": 30000,
        }

        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
        }

        # 4. 发送请求（超时 300 秒，大模型处理图片可能需要较长时间）
        logger.info("正在调用大模型 API: %s, model: %s", self.endpoint, self.model)
        timeout = httpx.Timeout(600.0, connect=30.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(
                self.endpoint, json=request_body, headers=headers
            )

        if resp.status_code != 200:
            logger.error(
                "模型调用失败，状态码: %s, 响应: %s",
                resp.status_code,
                resp.text,
            )
            raise RuntimeError(f"模型调用失败，HTTP {resp.status_code}")

        response_body = resp.text
        logger.debug("模型原始响应: %s", response_body[:500])

        # 5. 检查是否截断
        self._check_truncation(response_body)

        # 6. 提取 content 文本
        content_text = self._extract_content(response_body)
        logger.debug("模型返回的 content: %s", content_text[:300] if content_text else "空")

        if not content_text or not content_text.strip():
            raise RuntimeError("模型未返回有效内容")

        # 7. 提取 JSON
        json_str = self._extract_json(content_text)
        logger.debug("提取到的 JSON: %s", json_str[:200])

        # 8. 解析为 Pydantic 模型
        data = json.loads(json_str)
        return UnifiedGradingResult(**data)

    def _compress_image(self, image_bytes: bytes, max_size: int = 1200) -> bytes:
        """
        压缩图片至最大尺寸限制

        Args:
            image_bytes: 原始图片字节
            max_size: 最大宽/高

        Returns:
            压缩后的 PNG 字节
        """
        img = Image.open(BytesIO(image_bytes))

        # 转换为 RGB（处理 RGBA 等情况）
        if img.mode != "RGB":
            img = img.convert("RGB")

        w, h = img.size
        if w > max_size or h > max_size:
            ratio = min(max_size / w, max_size / h)
            new_w = int(w * ratio)
            new_h = int(h * ratio)
            img = img.resize((new_w, new_h), Image.Resampling.BILINEAR)
            logger.info("图片已压缩: %dx%d -> %dx%d", w, h, new_w, new_h)

        buf = BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue()

    def _build_prompt(self, standard_answer: str) -> str:
        """构建 Prompt"""
        return (
            "你是一位严谨的试卷批改老师。请分析这张试卷图片，完成以下任务：\n\n"
            "1. 识别出学生所有手写答案的位置，用边界框标出每个答案区域（坐标请返回像素值）。\n"
            "2. 识别每个答案区域内的手写文字内容。\n"
            "3. 将识别出的学生答案与标准答案进行比对。标准答案如下（每道题按顺序对应）：\n"
            f"{standard_answer}\n\n"
            "4. 对每个答案判断对错。\n"
            "   - 如果**正确**：请将 result 设为 \"正确\"，explanation 字段仅包含 "
            '"【解析】此题目的答案解析。"（绿色部分），不包含错因。\n'
            "   - 如果**错误**：请将 result 设为 \"错误\"，explanation 字段必须同时包含 "
            '"【解析】此题目的答案解析。" 和 "【错因】学生这道题的错因。" 两部分，'
            "且【错因】放在最后。\n"
            "   注意：explanation 中不要包含其他多余文字，"
            '格式务必为 "【解析】...【错因】..."（错误时）或 "【解析】..."（正确时）。\n'
            "5. 给出整体评价，格式必须严格为以下四行（每行以方括号标签开头），"
            '标签后直接跟内容，**不要添加"包括"、"为"等多余文字**：\n'
            "   练习总结：\n"
            "   【练习情况】...\n"
            "   【错题类型】...\n"
            "   【薄弱模块】...\n"
            "   【改进方法】...\n"
            "   请确保整体评价严格按此格式返回，每项内容简洁扼要，不加任何前缀。\n\n"
            "请严格按照以下JSON格式返回结果，不要输出任何其他内容：\n"
            '{\n'
            '  "overallComment": "整体评价，必须包含【练习情况】【错题类型】【薄弱模块】【改进方法】四个标签，格式如上述要求",\n'
            '  "questions": [\n'
            "    {\n"
            '      "studentAnswer": "识别出的学生手写答案文字",\n'
            '      "result": "正确" 或 "错误",\n'
            '      "explanation": "正确时为【解析】...，错误时为【解析】...【错因】...",\n'
            '      "errorAnalysis": "（始终为空字符串）",\n'
            '      "bbox": {\n'
            '        "x": 左上角x坐标,\n'
            '        "y": 左上角y坐标,\n'
            '        "width": 宽度,\n'
            '        "height": 高度\n'
            "      }\n"
            "    }\n"
            "  ]\n"
            "}"
        )

    def _check_truncation(self, response_body: str):
        """检查响应是否因内容过长被截断"""
        try:
            data = json.loads(response_body)
            choices = data.get("choices", [])
            if choices:
                finish_reason = choices[0].get("finish_reason", "")
                if finish_reason == "length":
                    logger.error("模型响应因内容过长被截断")
                    raise RuntimeError("批改结果因内容过长被截断，请尝试缩小批改范围或联系管理员调整模型参数")
        except json.JSONDecodeError:
            pass

    def _extract_content(self, response_body: str) -> str:
        """从 chat/completions 响应中提取 message.content"""
        try:
            data = json.loads(response_body)
            choices = data.get("choices", [])
            if choices:
                message = choices[0].get("message", {})
                return message.get("content", "")
            # 兼容其他格式
            if "output" in data:
                return self._extract_output_text(data)
        except json.JSONDecodeError:
            pass
        return ""

    def _extract_output_text(self, data: dict) -> str:
        """备用：从 /responses 格式提取"""
        output = data.get("output", [])
        if isinstance(output, list):
            for node in output:
                if node.get("type") == "message":
                    content = node.get("content", [])
                    for item in content:
                        if item.get("type") == "output_text":
                            return item.get("text", "")
        return ""

    def _extract_json(self, text: str) -> str:
        """
        从文本中提取 JSON（去除 markdown 代码块等）
        """
        # 匹配 ```json ... ``` 或 ``` ... ```
        match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
        if match:
            return match.group(1).strip()

        # 直接找第一个 { 和最后一个 }
        start = text.find("{")
        end = text.rfind("}")
        if start >= 0 and end > start:
            return text[start : end + 1]

        return text.strip()
