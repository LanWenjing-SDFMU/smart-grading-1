"""数据模型，对应 Java 的 GradeRequest, GradeResponse, UnifiedGradingResult"""

from pydantic import BaseModel
from typing import Optional


class BoundingBox(BaseModel):
    """边界框坐标"""
    x: int = 0
    y: int = 0
    width: int = 0
    height: int = 0


class QuestionResult(BaseModel):
    """单个题目的批改结果"""
    studentAnswer: str = ""
    correctAnswer: str = ""
    result: str = ""          # "正确" 或 "错误"
    explanation: str = ""     # 解析文本
    errorAnalysis: str = ""   # 错因分析
    bbox: Optional[BoundingBox] = None


class UnifiedGradingResult(BaseModel):
    """大模型返回的统一批改结果"""
    overallComment: str = ""
    questions: list[QuestionResult] = []


class GradeResponse(BaseModel):
    """批改响应（含交互标注数据）"""
    markedImageBase64: str = ""       # 带对错标记的图片 Base64
    originalImageBase64: str = ""     # 原图 Base64（前端用于覆盖层展示）
    result: str = ""
    explanation: str = ""
    errorAnalysis: str = ""
    overallComment: str = ""
    questions: list[QuestionResult] = []   # 标注数据，前端用于渲染可拖拽编辑的解析框
    pageFilename: str = ""            # 原始文件名


class BatchGradeResponse(BaseModel):
    """批量批改响应（多页试卷）"""
    pages: list[GradeResponse] = []
    totalPages: int = 0
