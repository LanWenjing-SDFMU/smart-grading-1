"""
API 路由，对应 Java 的 GradingController

POST /api/grading/upload          — 单页上传
POST /api/grading/batch-upload    — 多页批量上传
"""

import logging

import httpx
from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.models import BatchGradeResponse, GradeResponse
from app.services.grading_service import GradingService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/grading", tags=["批改"])
grading_service = GradingService()


@router.post("/upload", response_model=GradeResponse)
async def grade_upload(
    file: UploadFile = File(..., description="试卷图片"),
    standardAnswer: str = Form(..., description="标准答案"),
):
    """
    上传单张试卷图片和标准答案，进行智能批改

    Args:
        file: 试卷图片文件
        standardAnswer: 标准答案文本

    Returns:
        GradeResponse: 批改结果（含标注后的图片 Base64）
    """
    image_bytes = await file.read()
    logger.info(
        "收到批改请求: file=%s, size=%d, standardAnswer=%s",
        file.filename,
        len(image_bytes),
        standardAnswer[:50] if standardAnswer else "",
    )

    try:
        response = await grading_service.process_paper(image_bytes, standardAnswer)
        response.pageFilename = file.filename or "unknown.png"
    except httpx.TimeoutException:
        logger.error("调用大模型 API 超时")
        raise HTTPException(
            status_code=504,
            detail="AI 模型响应超时，请稍后重试或检查网络连接",
        )
    except Exception as e:
        logger.error("批改过程发生错误: %s", e, exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"批改失败: {str(e)}",
        )

    logger.info("批改完成，返回结果")
    return response


@router.post("/batch-upload", response_model=BatchGradeResponse)
async def grade_batch_upload(
    files: list[UploadFile] = File(..., description="多张试卷图片"),
    standardAnswer: str = Form(..., description="标准答案"),
):
    """
    上传多张试卷图片和标准答案，逐页独立批改

    Args:
        files: 多张试卷图片文件
        standardAnswer: 标准答案文本

    Returns:
        BatchGradeResponse: 批量批改结果（每页独立）
    """
    logger.info(
        "收到批量批改请求: files=%d, standardAnswer=%s",
        len(files),
        standardAnswer[:50] if standardAnswer else "",
    )

    pages = []
    for idx, file in enumerate(files):
        logger.info("正在批改第 %d 页: %s", idx + 1, file.filename)
        image_bytes = await file.read()
        try:
            response = await grading_service.process_paper(image_bytes, standardAnswer)
            response.pageFilename = file.filename or f"page_{idx + 1}.png"
            pages.append(response)
        except httpx.TimeoutException:
            logger.error("第 %d 页调用大模型 API 超时", idx + 1)
            pages.append(GradeResponse(
                result="超时",
                explanation="AI 模型响应超时，请稍后重试",
                pageFilename=file.filename or f"page_{idx + 1}.png",
            ))
        except Exception as e:
            logger.error("第 %d 页批改失败: %s", idx + 1, e, exc_info=True)
            pages.append(GradeResponse(
                result="错误",
                explanation=f"批改失败: {str(e)}",
                pageFilename=file.filename or f"page_{idx + 1}.png",
            ))

    logger.info("批量批改完成，共 %d 页", len(pages))
    return BatchGradeResponse(pages=pages, totalPages=len(pages))
