"""
智能阅卷系统 - Python 后端入口

对应 Java 的 SmartGradingApplication

启动方式：
    uvicorn main:app --reload --port 8080
    poetry run uvicorn main:app --reload --port 8080
"""

import logging

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers.grading_router import router as grading_router

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="智能阅卷系统",
    description="AI-powered Smart Grading System - Python 后端",
    version="1.0.0",
)

# CORS 配置（对应 Java 的 CorsConfig）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:5174"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(grading_router)


@app.get("/")
async def root():
    """健康检查"""
    return {"message": "智能阅卷系统后端运行中", "status": "ok"}


@app.get("/health")
async def health():
    """健康检查端点"""
    return {"status": "UP"}


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=settings.server_port,
        reload=True,
    )
