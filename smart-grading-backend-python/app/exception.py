"""业务异常，对应 Java 的 BusinessException"""


class BusinessException(Exception):
    """业务异常"""

    def __init__(self, message: str, code: str = None):
        super().__init__(message)
        self.code = code
        self.message = message
