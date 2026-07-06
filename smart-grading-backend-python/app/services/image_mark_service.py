"""
图像标注服务，对应 Java 的 ImageMarkService

功能：
1. 在试卷图片上绘制对错标记（✓ / ×）
2. 为错误题目绘制红色下划线
3. 为错误题目绘制解析文本框（自动避开重叠）
4. 绘制整体评价区域
5. 使用大画布模式，左右留空白放置解析框
"""

import logging
import math
import re as _re
from io import BytesIO
from typing import Optional

from PIL import Image, ImageDraw, ImageFont

from app.models import UnifiedGradingResult

logger = logging.getLogger(__name__)

# 尝试加载中文字体，Windows 常用路径
_FONT_PATHS = [
    "C:/Windows/Fonts/msyh.ttc",        # 微软雅黑
    "C:/Windows/Fonts/msyhbd.ttc",      # 微软雅黑粗体
    "C:/Windows/Fonts/simhei.ttf",      # 黑体
    "C:/Windows/Fonts/simsun.ttc",      # 宋体
    "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",  # Linux
    "/System/Library/Fonts/PingFang.ttc",              # macOS
]


def _get_font(size: int = 16, bold: bool = False) -> ImageFont.FreeTypeFont:
    """获取中文字体"""
    for path in _FONT_PATHS:
        try:
            return ImageFont.truetype(path, size=size)
        except (IOError, OSError):
            continue
    # 兜底：使用默认字体
    return ImageFont.load_default()


_COMMENT_HEIGHT = 180


class ImageMarkService:
    """图像标注服务"""

    async def mark_image_with_unified_result(
        self, image_bytes: bytes, result: UnifiedGradingResult
    ) -> bytes:
        """
        根据统一批改结果绘制批注（大画布模式）

        Args:
            image_bytes: 原图字节
            result: 批改结果

        Returns:
            标注后的 PNG 图片字节
        """
        logger.info("=== 开始绘制批注（大画布模式）===")
        original = Image.open(BytesIO(image_bytes)).convert("RGBA")
        orig_width, orig_height = original.size

        # ---- 1. 计算大画布尺寸和偏移量 ----
        margin_ratio = 0.35
        margin_x = int(orig_width * margin_ratio)
        margin_y = 10
        canvas_width = orig_width + 2 * margin_x
        canvas_height = orig_height + 2 * margin_y + _COMMENT_HEIGHT
        offset_x = margin_x
        offset_y = margin_y

        # ---- 2. 创建大画布 ----
        canvas = Image.new("RGBA", (canvas_width, canvas_height), (255, 255, 255, 255))
        canvas.paste(original, (offset_x, offset_y), original)

        draw = ImageDraw.Draw(canvas)

        # ---- 3. 准备障碍列表 ----
        paper_rect = (offset_x, offset_y, offset_x + orig_width, offset_y + orig_height)
        obstacles = [paper_rect]

        # ---- 4. 转换题目框坐标 ----
        questions = result.questions
        if not questions:
            logger.warning("没有题目，返回原图（大画布）")
            buf = BytesIO()
            canvas.save(buf, format="PNG")
            return buf.getvalue()

        translated_bounds: list[Optional[tuple]] = []
        for q in questions:
            bbox = self._convert_bbox(q.bbox, orig_width, orig_height)
            if bbox is None:
                translated_bounds.append(None)
                continue
            x, y, w, h = bbox
            translated = (x + offset_x, y + offset_y, x + offset_x + w, y + offset_y + h)
            translated_bounds.append(translated)
            obstacles.append(translated)

        # ---- 5. 记录已放置解析框 ----
        used_annotation_rects: list[tuple] = []
        max_allowed_y = offset_y + orig_height

        # ---- 6. 遍历题目绘制 ----
        for i, q in enumerate(questions):
            bounds = translated_bounds[i]
            if bounds is None:
                continue

            # 绘制对错号
            self._draw_mark(draw, bounds, q.result)

            # 如果错误，绘制下划线
            if q.result == "错误":
                self._draw_underline(draw, bounds)

            # 仅错误且有解析才绘制解析框和连线
            if q.result == "错误" and q.explanation:
                placed_rect = self._draw_annotation_with_placement(
                    draw, bounds, q.explanation,
                    canvas_width, canvas_height,
                    obstacles, used_annotation_rects,
                    margin_x, max_allowed_y
                )
                if placed_rect:
                    used_annotation_rects.append(placed_rect)

        # ---- 7. 绘制整体评价 ----
        self._draw_overall_comment(
            draw, result.overallComment,
            canvas_width, canvas_height,
            offset_y, orig_height
        )

        buf = BytesIO()
        # 转换回 RGB 保存为 PNG
        final = Image.new("RGB", canvas.size, (255, 255, 255))
        final.paste(canvas, mask=canvas.split()[3])  # 用 alpha 通道作为 mask
        final.save(buf, format="PNG")
        logger.info("大画布绘制完成，尺寸: %dx%d", canvas_width, canvas_height)
        return buf.getvalue()

    def _convert_bbox(self, bbox, img_width: int, img_height: int) -> Optional[tuple]:
        """将 BoundingBox 转换为 (x, y, w, h)，处理归一化坐标和边界裁剪"""
        if bbox is None:
            return None
        x, y, w, h = bbox.x, bbox.y, bbox.width, bbox.height

        # 归一化坐标（0~1000）缩放
        if x <= 1000 and y <= 1000 and w <= 1000 and h <= 1000:
            x = int(x * img_width / 1000.0)
            y = int(y * img_height / 1000.0)
            w = int(w * img_width / 1000.0)
            h = int(h * img_height / 1000.0)

        # 边界裁剪
        x = max(0, x)
        y = max(0, y)
        if x + w > img_width:
            w = img_width - x
        if y + h > img_height:
            h = img_height - y
        if w <= 0 or h <= 0:
            return None
        return (x, y, w, h)

    def _draw_mark(self, draw: ImageDraw, bounds: tuple, result: str):
        """绘制对错标记 ✓ / ×"""
        x, y, x2, y2 = bounds
        bw = x2 - x
        bh = y2 - y
        mark_size = 28
        font = _get_font(size=mark_size, bold=True)

        mark = "√" if result == "正确" else "×"
        color = (34, 197, 94) if result == "正确" else (220, 38, 38)

        # 计算居中位置
        bbox_text = draw.textbbox((0, 0), mark, font=font)
        tw = bbox_text[2] - bbox_text[0]
        th = bbox_text[3] - bbox_text[1]
        tx = x + (bw - tw) // 2
        ty = y + (bh - th) // 2

        draw.text((tx, ty), mark, fill=color, font=font)

    def _draw_underline(self, draw: ImageDraw, bounds: tuple):
        """绘制错误下划线"""
        x, y, x2, y2 = bounds
        line_y = y2 + 4
        draw.line([(x, line_y), (x2, line_y)], fill=(220, 38, 38), width=3)

    def _draw_annotation_with_placement(
        self, draw: ImageDraw, bounds: tuple, explanation: str,
        img_width: int, img_height: int,
        obstacles: list, used_rects: list,
        margin_x: int, max_allowed_y: int
    ) -> Optional[tuple]:
        """
        为错误题目绘制解析文本和连线，自动寻找不重叠位置

        放置优先级：上、右、左（下方留给整体评价）
        """
        if not explanation:
            return None

        font_size = 16
        font = _get_font(size=font_size)
        line_height = font_size + 6

        # 动态调整最大框宽
        max_width = min(margin_x - 30, 380)
        max_width = max(max_width, 150)

        # 1. 拆分【解析】和【错因】
        parse_part = explanation
        error_part = ""
        if "【错因】" in explanation:
            idx = explanation.index("【错因】")
            parse_part = explanation[:idx].strip()
            error_part = "【错因】" + explanation[idx + 4:].strip()

        # 2. 分别换行
        parse_lines = self._wrap_text(parse_part, font, max_width)
        error_lines = self._wrap_text(error_part, font, max_width)

        # 3. 合并行
        all_lines = list(parse_lines)
        if error_lines:
            all_lines.extend(error_lines)

        # 4. 计算尺寸
        text_width = max(draw.textbbox((0, 0), line, font=font)[2] for line in all_lines)
        text_height = len(all_lines) * line_height
        padding_text = 4
        extra_padding = 8
        extra_vertical = 4
        total_width = text_width + 2 * padding_text + extra_padding
        total_height = text_height + 2 * padding_text + extra_vertical

        bx, by, bx2, by2 = bounds
        b_center_x = (bx + bx2) // 2
        b_center_y = (by + by2) // 2

        all_obstacles = list(obstacles) + list(used_rects)
        margin = 15

        # 5. 生成候选位置
        candidates = []
        offsets = [10 + i * 25 for i in range(15)]
        dy_values = [-80 + i * 20 for i in range(9)]

        # 优先尝试右侧和左侧
        dist_left = bx
        dist_right = img_width - bx2
        if dist_right >= dist_left:
            dir_order = ["right", "left"]
        else:
            dir_order = ["left", "right"]

        for direction in dir_order:
            for offset in offsets:
                if direction == "right":
                    rx = bx2 + offset
                else:
                    rx = bx - offset - total_width

                if rx < 0 or rx + total_width > img_width:
                    continue

                for dy in dy_values:
                    ry = b_center_y - total_height // 2 + dy
                    ry = max(0, min(img_height - total_height, ry))
                    if ry + total_height > max_allowed_y - margin:
                        continue
                    rect = (rx, ry, rx + total_width, ry + total_height)
                    if not self._is_overlapping(rect, all_obstacles, margin):
                        candidates.append(rect)

        chosen_rect = None
        if candidates:
            candidates.sort(key=lambda r: self._distance(r, bounds))
            chosen_rect = candidates[0]
        else:
            # 6. 精细全局搜索
            logger.warning("局部候选无效，启动精细全局搜索...")
            step = 15
            best_dist = float("inf")
            best_rect = None
            # 左侧区域
            for rx in range(0, max(margin_x - total_width, 0) + 1, step):
                for ry in range(0, max(max_allowed_y - total_height - margin, 0) + 1, step):
                    rect = (rx, ry, rx + total_width, ry + total_height)
                    if rect[3] > max_allowed_y - margin:
                        continue
                    if not self._is_overlapping(rect, all_obstacles, margin):
                        d = self._distance(rect, bounds)
                        if d < best_dist:
                            best_dist = d
                            best_rect = rect
            # 右侧区域
            for rx in range(max(img_width - margin_x, 0), max(img_width - total_width, 0) + 1, step):
                for ry in range(0, max(max_allowed_y - total_height - margin, 0) + 1, step):
                    rect = (rx, ry, rx + total_width, ry + total_height)
                    if rect[3] > max_allowed_y - margin:
                        continue
                    if not self._is_overlapping(rect, all_obstacles, margin):
                        d = self._distance(rect, bounds)
                        if d < best_dist:
                            best_dist = d
                            best_rect = rect
            if best_rect is not None:
                chosen_rect = best_rect

        if chosen_rect is None:
            # 7. 宽松回退策略
            logger.warning("精细全局搜索失败，使用宽松回退策略")
            hard_obstacles = []
            for obs in obstacles:
                ox, oy, ox2, oy2 = obs
                ow = ox2 - ox
                oh = oy2 - oy
                # 跳过试卷区域
                if ow > img_width * 0.8 and oh > img_height * 0.8:
                    continue
                hard_obstacles.append(obs)

            fallback_rect = None
            best_fallback_dist = float("inf")
            # 左右区域搜索
            search_ranges = [
                (0, max(margin_x - total_width, 0)),
                (max(img_width - margin_x, 0), max(img_width - total_width, 0)),
            ]
            for rx_start, rx_end in search_ranges:
                for rx in range(rx_start, max(rx_end + 1, 0), 10):
                    for ry in range(0, max(max_allowed_y - total_height - margin, 0) + 1, 10):
                        rect = (rx, ry, rx + total_width, ry + total_height)
                        if rect[3] > max_allowed_y - margin:
                            continue
                        overlaps_hard = any(
                            self._rects_intersect(rect, hard)
                            for hard in hard_obstacles
                        )
                        if not overlaps_hard:
                            d = self._distance(rect, bounds)
                            if d < best_fallback_dist:
                                best_fallback_dist = d
                                fallback_rect = rect
            if fallback_rect is not None:
                chosen_rect = fallback_rect
            else:
                chosen_rect = (margin_x + 10, 10, margin_x + 10 + total_width, 10 + total_height)
                logger.warning("所有回退失败，使用绝对保险位置（左上角）")

        # 绘制解析框背景
        rx, ry, rx2, ry2 = chosen_rect
        draw.rectangle([rx, ry, rx2, ry2], fill=(255, 255, 255, 220), outline=(255, 0, 0))

        # 绘制文字（绿色为解析，红色为错因）
        text_x = rx + padding_text
        text_y = ry + padding_text
        is_error_part = False
        for line in all_lines:
            if line.startswith("【错因】"):
                is_error_part = True
            color = (220, 38, 38) if is_error_part else (34, 197, 94)
            draw.text((text_x, text_y), line, fill=color, font=font)
            text_y += line_height

        # 绘制连线
        p1, p2 = self._closest_points(bounds, chosen_rect)
        draw.line([p1, p2], fill=(255, 0, 0), width=2)
        draw.ellipse([p1[0] - 3, p1[1] - 3, p1[0] + 3, p1[1] + 3], fill=(255, 0, 0))
        draw.ellipse([p2[0] - 3, p2[1] - 3, p2[0] + 3, p2[1] + 3], fill=(255, 0, 0))

        return chosen_rect

    def _wrap_text(self, text: str, font, max_width: int) -> list:
        """将文本按指定宽度换行"""
        lines = []
        if not text:
            return lines

        # 按换行符拆分
        for paragraph in text.split("\n"):
            if not paragraph:
                continue
            line = ""
            for ch in paragraph:
                test_line = line + ch
                bbox = font.getbbox(test_line)
                tw = bbox[2] - bbox[0]
                if tw > max_width and line:
                    lines.append(line)
                    line = ch
                else:
                    line = test_line
            if line:
                lines.append(line)
        return lines

    @staticmethod
    def _is_overlapping(rect: tuple, rects: list, margin: int = 0) -> bool:
        """检查矩形是否与列表中的任意矩形重叠（带间距）"""
        rx, ry, rx2, ry2 = rect
        rx -= margin
        ry -= margin
        rx2 += margin
        ry2 += margin
        for r in rects:
            ox, oy, ox2, oy2 = r
            if rx < ox2 and rx2 > ox and ry < oy2 and ry2 > oy:
                return True
        return False

    @staticmethod
    def _rects_intersect(r1: tuple, r2: tuple) -> bool:
        """检查两个矩形是否相交"""
        return r1[0] < r2[2] and r1[2] > r2[0] and r1[1] < r2[3] and r1[3] > r2[1]

    @staticmethod
    def _distance(rect: tuple, target: tuple) -> float:
        """计算两个矩形中心点距离"""
        cx1 = (rect[0] + rect[2]) / 2
        cy1 = (rect[1] + rect[3]) / 2
        cx2 = (target[0] + target[2]) / 2
        cy2 = (target[1] + target[3]) / 2
        return math.hypot(cx1 - cx2, cy1 - cy2)

    @staticmethod
    def _closest_points(a: tuple, b: tuple):
        """
        计算两个矩形之间的最近点对
        返回 (p1, p2)，p1 在矩形 a 上，p2 在矩形 b 上
        """
        ax1, ay1, ax2, ay2 = a
        bx1, by1, bx2, by2 = b

        # 判断相对方位
        left = ax2 <= bx1
        right = bx2 <= ax1
        above = ay2 <= by1
        below = by2 <= ay1

        if left:
            x1, x2 = ax2, bx1
            if ay2 > by1 and ay1 < by2:  # Y 方向重叠
                top = max(ay1, by1)
                bottom = min(ay2, by2)
                mid_y = (top + bottom) / 2
                y1 = y2 = mid_y
            elif ay2 < by1:  # A 在 B 上方
                y1, y2 = ay2, by1
            else:  # A 在 B 下方
                y1, y2 = ay1, by2
        elif right:
            x1, x2 = ax1, bx2
            if ay2 > by1 and ay1 < by2:
                top = max(ay1, by1)
                bottom = min(ay2, by2)
                mid_y = (top + bottom) / 2
                y1 = y2 = mid_y
            elif ay2 < by1:
                y1, y2 = ay2, by1
            else:
                y1, y2 = ay1, by2
        elif above:
            y1, y2 = ay2, by1
            if ax2 > bx1 and ax1 < bx2:
                left_x = max(ax1, bx1)
                right_x = min(ax2, bx2)
                mid_x = (left_x + right_x) / 2
                x1 = x2 = mid_x
            elif ax2 < bx1:
                x1, x2 = ax2, bx1
            else:
                x1, x2 = ax1, bx2
        elif below:
            y1, y2 = ay1, by2
            if ax2 > bx1 and ax1 < bx2:
                left_x = max(ax1, bx1)
                right_x = min(ax2, bx2)
                mid_x = (left_x + right_x) / 2
                x1 = x2 = mid_x
            elif ax2 < bx1:
                x1, x2 = ax2, bx1
            else:
                x1, x2 = ax1, bx2
        else:
            # 重叠情况，返回中心连线
            cx1 = (ax1 + ax2) / 2
            cy1 = (ay1 + ay2) / 2
            cx2 = (bx1 + bx2) / 2
            cy2 = (by1 + by2) / 2
            return ((cx1, cy1), (cx2, cy2))

        return ((x1, y1), (x2, y2))

    def _draw_overall_comment(
        self, draw: ImageDraw, comment: str,
        canvas_width: int, canvas_height: int,
        orig_y: int, orig_height: int
    ):
        """绘制整体评价文本（在画布底部评价区域）"""
        if not comment:
            return

        # 清洗评价文本
        comment = _re.sub(r"【错题类型】[：:]*\s*错题类型包括[：:]\s*", "【错题类型】", comment)
        comment = _re.sub(r"【薄弱模块】[：:]*\s*薄弱模块为[：:]\s*", "【薄弱模块】", comment)

        # 评价区域背景
        comment_y = orig_y + orig_height + 20
        draw.rectangle(
            [0, comment_y, canvas_width, comment_y + _COMMENT_HEIGHT - 10],
            fill=(240, 240, 240, 200)
        )
        draw.line([(0, comment_y), (canvas_width, comment_y)], fill=(128, 128, 128), width=1)

        # 绘制文本
        font = _get_font(size=16)
        y = comment_y + 20
        max_text_width = canvas_width - 40
        lines = self._wrap_text(comment, font, max_text_width)
        for line in lines:
            draw.text((20, y), line, fill=(0, 0, 0), font=font)
            y += font.size + 6
            if y > comment_y + _COMMENT_HEIGHT - 30:
                break
