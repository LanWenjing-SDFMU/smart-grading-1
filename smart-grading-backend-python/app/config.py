"""应用程序配置，对应 Java 的 LlmProperties 和 application.yml"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置，从环境变量或 .env 文件加载"""

    # 服务端口
    server_port: int = 8080

    # 大模型配置（主：火山引擎/豆包）
    ai_llm_api_key: str = "ark-4ef84834-d169-4130-b86f-cc45e081061e-0b95f"
    ai_llm_model: str = "doubao-seed-evolving"
    ai_llm_endpoint: str = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

    # 备用火山引擎配置
    ai_volcano_api_key: str = "ark-4ef84834-d169-4130-b86f-cc45e081061e-0b95f"
    ai_volcano_model: str = "doubao-seed-2-1-pro-260628"
    ai_volcano_endpoint: str = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

    model_config = {"env_prefix": "", "env_file": ".env", "extra": "ignore"}


settings = Settings()
