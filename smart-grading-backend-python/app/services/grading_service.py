"""
业务编排服务，对应 Java 的 GradingService

功能：
1. 调用大模型进行批改
2. 调用图像标注服务绘制批注
3. 组装响应结果（含交互用标注数据）
"""

import base64
import logging
from io import BytesIO

from PIL import Image

from app.models import GradeResponse
from app.services.unified_grading_service import UnifiedGradingService
from app.services.image_mark_service import ImageMarkService

logger = logging.getLogger(__name__)


class GradingService:
    """批改服务：编排大模型调用和图像标注流程"""

    def __init__(self):
        self.unified_grading_service = UnifiedGradingService()
        self.image_mark_service = ImageMarkService()

    async def process_paper(self, image_bytes: bytes, standard_answer: str) -> GradeResponse:
        """
        处理试卷批改

        Args:
            image_bytes: 试卷图片字节
            standard_answer: 标准答案文本

        Returns:
            GradeResponse: 批改响应（含交互标注数据）
        """
        # 1. 一次调用完成所有任务：识别 + 定位 + 判断 + 解析
        logger.info("=== 开始批改试卷 ===")
        grading_result = await self.unified_grading_service.grade_paper_with_location(
            image_bytes, standard_answer
        )

        # 2. 生成带批注的图片（用于后端存档 / 备用）
        marked_image_bytes = await self.image_mark_service.mark_image_with_unified_result(
            image_bytes, grading_result
        )
        try:
            with open("marked_result.png", "wb") as f:
                f.write(marked_image_bytes)
            logger.info("标记后图片已保存到项目根目录：marked_result.png")
        except Exception as e:
            logger.error("保存图片失败: %s", e)

        # 3. 压缩原图，供前端做可拖拽编辑的底图
        original_compressed = self._compress_original(image_bytes)
        original_b64 = base64.b64encode(original_compressed).decode("utf-8")

        # 4. 组装响应
        questions_data = []
        if grading_result.questions:
            for q in grading_result.questions:
                questions_data.append(q)

        response = GradeResponse(
            markedImageBase64=base64.b64encode(marked_image_bytes).decode("utf-8"),
            originalImageBase64=original_b64,
            overallComment=grading_result.overallComment or "",
            questions=questions_data,
        )

        # 取第一题的结果作为整体摘要
        if grading_result.questions:
            first = grading_result.questions[0]
            response.result = first.result or ""
            response.explanation = first.explanation or ""
            response.errorAnalysis = first.errorAnalysis or ""
        else:
            response.result = "未知"
            response.explanation = "未识别到任何答案"
            response.errorAnalysis = ""

        logger.info("=== 批改完成 ===")
        return response

    def _compress_original(self, image_bytes: bytes, max_size: int = 1600) -> bytes:
        """压缩原图（供前端展示）"""
        img = Image.open(BytesIO(image_bytes))
        if img.mode != "RGB":
            img = img.convert("RGB")
        w, h = img.size
        if w > max_size or h > max_size:
            ratio = min(max_size / w, max_size / h)
            new_w = int(w * ratio)
            new_h = int(h * ratio)
            img = img.resize((new_w, new_h), Image.Resampling.BILINEAR)
        buf = BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue()
