package com.example.smartgrading.service;

import com.example.smartgrading.model.UnifiedGradingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageMarkService {

    private static final int COMMENT_HEIGHT = 180; // 评价区域高度（原150）

    /**
     * 根据统一批改结果绘制批注（大画布模式，支持将解析框放置到试卷外部空白区域）
     */
    public byte[] markImageWithUnifiedResult(MultipartFile file, UnifiedGradingResult result) throws IOException {
        log.info("=== 开始绘制批注（大画布模式）===");
        BufferedImage original = ImageIO.read(file.getInputStream());
        int origWidth = original.getWidth();
        int origHeight = original.getHeight();

        // ---- 1. 计算大画布尺寸和偏移量 ----
        double marginRatio = 0.2; // 左右各20%空白
        int marginX = (int)(origWidth * marginRatio);
        int marginY = 10; // 上边距保留少量，仅用于美观
        int canvasWidth = origWidth + 2 * marginX;
        int canvasHeight = origHeight + 2 * marginY + COMMENT_HEIGHT;
        int offsetX = marginX;
        int offsetY = marginY;

        // ---- 2. 创建大画布，绘制原图到中心 ----
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, offsetX, offsetY, null);

        // ---- 3. 准备障碍列表：先加入试卷区域（原图矩形） ----
        Rectangle paperRect = new Rectangle(offsetX, offsetY, origWidth, origHeight);
        List<Rectangle> obstacles = new ArrayList<>();
        obstacles.add(paperRect);

        // ---- 4. 转换题目框坐标并加入障碍 ----
        // 先收集所有题目框（平移后）
        List<UnifiedGradingResult.QuestionResult> questions = result.getQuestions();
        if (questions == null || questions.isEmpty()) {
            log.warn("没有题目，返回原图（大画布）");
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(canvas, "png", baos);
            return baos.toByteArray();
        }

        // 存放每个题目平移后的边界框（用于绘制）
        List<Rectangle> translatedBounds = new ArrayList<>();
        for (UnifiedGradingResult.QuestionResult q : questions) {
            Rectangle bbox = convertBBox(q.getBbox(), origWidth, origHeight);
            if (bbox == null || bbox.width <= 0 || bbox.height <= 0) {
                translatedBounds.add(null);
                continue;
            }
            // 平移坐标
            Rectangle translated = new Rectangle(bbox.x + offsetX, bbox.y + offsetY,
                                                 bbox.width, bbox.height);
            translatedBounds.add(translated);
            // 加入障碍（防止解析框覆盖题目）
            obstacles.add(translated);
        }

        // ---- 5. 记录已放置解析框 ----
        List<Rectangle> usedAnnotationRects = new ArrayList<>();

        // ---- 5.1 计算试卷底部 Y 坐标 ----
        int maxAllowedY = offsetY + origHeight; // 试卷底部

        // ---- 6. 遍历题目绘制 ----
        for (int i = 0; i < questions.size(); i++) {
            UnifiedGradingResult.QuestionResult q = questions.get(i);
            Rectangle bounds = translatedBounds.get(i);
            if (bounds == null) continue;

            // 绘制对错号（正中间）
            drawMark(g, bounds, q.getResult());

            // 如果错误，绘制下划线
            if ("错误".equals(q.getResult())) {
                drawUnderline(g, bounds);
            }

            // 仅错误且有解析才绘制解析框和连线
            if ("错误".equals(q.getResult()) && q.getExplanation() != null && !q.getExplanation().isEmpty()) {
                // 调用放置方法，允许放到试卷外部
                Rectangle placedRect = drawAnnotationWithPlacement(
                        g, bounds, q.getExplanation(),
                        canvasWidth, canvasHeight,
                        obstacles, usedAnnotationRects,
                        marginX,
                        maxAllowedY
                );
                if (placedRect != null) {
                    usedAnnotationRects.add(placedRect);
                    // 也可将解析框加入障碍，防止后续覆盖（已在drawAnnotationWithPlacement内部加入）
                }
            }
        }

        // ---- 7. 绘制整体评价文本 ----
        drawOverallComment(g, result.getOverallComment(), canvasWidth, canvasHeight, offsetY, origHeight);

        g.dispose();

        // ---- 8. 输出大画布 ----
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", baos);
        log.info("大画布绘制完成，尺寸：{}x{}", canvasWidth, canvasHeight);
        return baos.toByteArray();
    }

    /**
     * 将 BoundingBox 转换为 Rectangle，并处理归一化坐标和边界裁剪
     */
    private Rectangle convertBBox(UnifiedGradingResult.BoundingBox bbox, int imgWidth, int imgHeight) {
        if (bbox == null) return null;
        int x = bbox.getX();
        int y = bbox.getY();
        int w = bbox.getWidth();
        int h = bbox.getHeight();

        // 归一化坐标（0~1000）缩放
        if (x <= 1000 && y <= 1000 && w <= 1000 && h <= 1000) {
            x = (int) (x * imgWidth / 1000.0);
            y = (int) (y * imgHeight / 1000.0);
            w = (int) (w * imgWidth / 1000.0);
            h = (int) (h * imgHeight / 1000.0);
        }

        // 边界裁剪
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + w > imgWidth) w = imgWidth - x;
        if (y + h > imgHeight) h = imgHeight - y;
        if (w <= 0 || h <= 0) return null;
        return new Rectangle(x, y, w, h);
    }

    /**
     * 为错误题目绘制解析文本和连线，并自动寻找不重叠的位置
     * 放置优先级顺序：上、右、左、下（下方留给整体评价）
     * @return 实际放置的矩形（用于后续碰撞检测），若无法放置则返回 null
     */
    private Rectangle drawAnnotationWithPlacement(Graphics2D g, Rectangle bounds, String explanation,
                                              int imgWidth, int imgHeight,
                                              List<Rectangle> obstacles, List<Rectangle> usedRects,
                                              int marginX, int maxAllowedY) {
    if (explanation == null || explanation.isEmpty()) return null;

    int fontSize = 16;
    Font font = new Font("微软雅黑", Font.PLAIN, fontSize);
    g.setFont(font);
    FontMetrics fm = g.getFontMetrics();

    // 根据左右空白宽度动态调整最大框宽
    int maxWidth = Math.min(marginX - 30, 380);
    maxWidth = Math.max(maxWidth, 150);
    List<String> lines = wrapText(explanation, fm, maxWidth);
    int lineHeight = fm.getHeight() + 2;
    int textHeight = lines.size() * lineHeight;
    int textWidth = 0;
    for (String line : lines) {
        int w = fm.stringWidth(line);
        if (w > textWidth) textWidth = w;
    }
    int paddingText = 4;
    int extraPadding = 8;
    int extraVertical = 4;
    int totalWidth = textWidth + 2 * paddingText + extraPadding;
    int totalHeight = textHeight + 2 * paddingText + extraVertical;

    List<Rectangle> allObstacles = new ArrayList<>(obstacles);
    allObstacles.addAll(usedRects);

    int margin = 15;

    // ----- 1. 生成更密集的候选位置 -----
    List<Rectangle> candidates = new ArrayList<>();
    int[] offsets = new int[15];
    for (int i = 0; i < offsets.length; i++) {
        offsets[i] = 10 + i * 25; // 最大 10 + 14*25 = 360
    }
    // 垂直方向更丰富的偏移量
    int[] dyValues = new int[9];
    for (int i = 0; i < 9; i++) {
        dyValues[i] = -80 + i * 20; // -80, -60, -40, -20, 0, 20, 40, 60, 80
    }

    // 优先尝试右侧和左侧（动态顺序基于距离）
    double distLeft = bounds.x;
    double distRight = imgWidth - (bounds.x + bounds.width);
    List<String> directionOrder;
    if (distRight >= distLeft) {
        directionOrder = Arrays.asList("right", "left");
    } else {
        directionOrder = Arrays.asList("left", "right");
    }

    for (String dir : directionOrder) {
        if ("right".equals(dir)) {
            for (int offset : offsets) {
                int x = bounds.x + bounds.width + offset;
                if (x + totalWidth < imgWidth) {
                    for (int dy : dyValues) {
                        int y = bounds.y + bounds.height/2 - totalHeight/2 + dy;
                        y = Math.max(0, Math.min(imgHeight - totalHeight, y));
                        if (y + totalHeight > maxAllowedY - margin) continue;
                        Rectangle rect = new Rectangle(x, y, totalWidth, totalHeight);
                        if (!isOverlapping(rect, allObstacles, margin)) {
                            candidates.add(rect);
                        }
                    }
                }
            }
        } else if ("left".equals(dir)) {
            for (int offset : offsets) {
                int x = bounds.x - offset - totalWidth;
                if (x >= 0) {
                    for (int dy : dyValues) {
                        int y = bounds.y + bounds.height/2 - totalHeight/2 + dy;
                        y = Math.max(0, Math.min(imgHeight - totalHeight, y));
                        if (y + totalHeight > maxAllowedY - margin) continue;
                        Rectangle rect = new Rectangle(x, y, totalWidth, totalHeight);
                        if (!isOverlapping(rect, allObstacles, margin)) {
                            candidates.add(rect);
                        }
                    }
                }
            }
        }
    }

    Rectangle chosenRect = null;
    if (!candidates.isEmpty()) {
        candidates.sort((r1, r2) -> Double.compare(distance(r1, bounds), distance(r2, bounds)));
        chosenRect = candidates.get(0);
    } else {
        // ----- 2. 全局搜索（步长缩小为15，提高命中率）-----
        log.warn("局部候选无效，启动精细全局搜索...");
        int step = 15;
        double bestDist = Double.MAX_VALUE;
        Rectangle bestRect = null;
        // 左侧区域
        for (int x = 0; x <= marginX - totalWidth && x < imgWidth; x += step) {
            for (int y = 0; y <= maxAllowedY - totalHeight - margin; y += step) {
                Rectangle rect = new Rectangle(x, y, totalWidth, totalHeight);
                if (rect.y + rect.height > maxAllowedY - margin) continue;
                if (!isOverlapping(rect, allObstacles, margin)) {
                    double d = distance(rect, bounds);
                    if (d < bestDist) {
                        bestDist = d;
                        bestRect = rect;
                    }
                }
            }
        }
        // 右侧区域
        for (int x = imgWidth - marginX; x <= imgWidth - totalWidth; x += step) {
            for (int y = 0; y <= maxAllowedY - totalHeight - margin; y += step) {
                Rectangle rect = new Rectangle(x, y, totalWidth, totalHeight);
                if (rect.y + rect.height > maxAllowedY - margin) continue;
                if (!isOverlapping(rect, allObstacles, margin)) {
                    double d = distance(rect, bounds);
                    if (d < bestDist) {
                        bestDist = d;
                        bestRect = rect;
                    }
                }
            }
        }
        if (bestRect != null) {
            chosenRect = bestRect;
        } else {
            // ----- 3. 回退策略：在左右边缘区域内找一个不覆盖题目框的位置（允许轻微重叠）-----
            log.warn("精细全局搜索也失败，使用宽松回退策略");
            // 只把题目框作为硬障碍（不能覆盖），解析框之间允许重叠（但尽量避免）
            List<Rectangle> hardObstacles = new ArrayList<>();
            for (Rectangle obs : obstacles) {
                // obstacles中包含了试卷区域和题目框，我们只保留题目框作为硬障碍
                // 但试卷区域（paperRect）不能覆盖，否则会盖住原图
                if (obs.x == 0 && obs.y == 0) continue; // 跳过试卷区域（实际无法精确判断，这里用坐标近似）
                // 更精确：如果矩形面积较大（超过原图一半），认为是试卷区域，跳过
                if (obs.width > imgWidth * 0.8 && obs.height > imgHeight * 0.8) continue;
                hardObstacles.add(obs);
            }
            // 在左右两侧区域搜索，不要求与解析框无碰撞，只要求不覆盖题目框
            int searchMargin = 20;
            Rectangle fallbackRect = null;
            double bestFallbackDist = Double.MAX_VALUE;
            for (int x = 0; x <= marginX - totalWidth && x < imgWidth; x += 10) {
                for (int y = 0; y <= maxAllowedY - totalHeight - margin; y += 10) {
                    Rectangle rect = new Rectangle(x, y, totalWidth, totalHeight);
                    if (rect.y + rect.height > maxAllowedY - margin) continue;
                    boolean overlapsHard = false;
                    for (Rectangle hard : hardObstacles) {
                        if (hard.intersects(rect)) { overlapsHard = true; break; }
                    }
                    if (!overlapsHard) {
                        double d = distance(rect, bounds);
                        if (d < bestFallbackDist) {
                            bestFallbackDist = d;
                            fallbackRect = rect;
                        }
                    }
                }
            }
            // 右侧区域
            for (int x = imgWidth - marginX; x <= imgWidth - totalWidth; x += 10) {
                for (int y = 0; y <= maxAllowedY - totalHeight - margin; y += 10) {
                    Rectangle rect = new Rectangle(x, y, totalWidth, totalHeight);
                    if (rect.y + rect.height > maxAllowedY - margin) continue;
                    boolean overlapsHard = false;
                    for (Rectangle hard : hardObstacles) {
                        if (hard.intersects(rect)) { overlapsHard = true; break; }
                    }
                    if (!overlapsHard) {
                        double d = distance(rect, bounds);
                        if (d < bestFallbackDist) {
                            bestFallbackDist = d;
                            fallbackRect = rect;
                        }
                    }
                }
            }
            if (fallbackRect != null) {
                chosenRect = fallbackRect;
            } else {
                // 最后保险：放在右上角（但尽量不覆盖原图）
                chosenRect = new Rectangle(marginX + 10, 10, totalWidth, totalHeight);
                log.warn("所有回退失败，使用绝对保险位置（右上角）");
            }
        }
    }

    // ----- 绘制解析框背景（半透明）和文字 -----
    g.setColor(new Color(255, 255, 255, 220));
    g.fillRect(chosenRect.x, chosenRect.y, chosenRect.width, chosenRect.height);
    g.setColor(Color.RED);
    g.drawRect(chosenRect.x, chosenRect.y, chosenRect.width, chosenRect.height);

    // ---- 拆分【解析】和【错因】 ----
    String explanationText = explanation;
    String parsePart = "";
    String errorPart = "";
    if (explanationText.contains("【错因】")) {
        int idx = explanationText.indexOf("【错因】");
        parsePart = explanationText.substring(0, idx).trim();
        errorPart = "【错因】" + explanationText.substring(idx + "【错因】".length()).trim();
    } else {
        parsePart = explanationText.trim();
    }

    // 分别对两部分进行换行
    int drawX = chosenRect.x + paddingText;
    int drawY = chosenRect.y + paddingText + fm.getAscent();

    // 绘制解析部分（绿色）
    if (!parsePart.isEmpty()) {
        g.setColor(new Color(34, 197, 94)); // 绿色
        List<String> parseLines = wrapText(parsePart, fm, maxWidth);
        for (String line : parseLines) {
            g.drawString(line, drawX, drawY);
            drawY += lineHeight;
        }
    }

    // 绘制错因部分（红色），如果有
    if (!errorPart.isEmpty()) {
        // 换行增加间距
        drawY += 4;
        g.setColor(new Color(220, 38, 38)); // 红色
        List<String> errorLines = wrapText(errorPart, fm, maxWidth);
        for (String line : errorLines) {
            g.drawString(line, drawX, drawY);
            drawY += lineHeight;
        }
    }

    Point2D[] pts = closestPoints(bounds, chosenRect);
    g.setColor(Color.RED);
    g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.drawLine((int)pts[0].getX(), (int)pts[0].getY(), (int)pts[1].getX(), (int)pts[1].getY());
    g.fillOval((int)pts[0].getX() - 3, (int)pts[0].getY() - 3, 6, 6);
    g.fillOval((int)pts[1].getX() - 3, (int)pts[1].getY() - 3, 6, 6);

    // 将新放置的解析框加入障碍列表（供后续题目使用）
    allObstacles.add(chosenRect); // 注意：这里修改的是局部变量，外部通过 usedRects 传递

    return chosenRect;
}

    /**
     * 检查矩形是否与列表中的任意矩形重叠
     */
    private boolean isOverlapping(Rectangle rect, List<Rectangle> rects) {
        for (Rectangle r : rects) {
            if (r.intersects(rect)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查矩形是否与给定的障碍列表重叠（带最小间距）
     */
    private boolean isOverlapping(Rectangle rect, List<Rectangle> obstacles, int margin) {
        Rectangle expanded = new Rectangle(rect.x - margin, rect.y - margin,
                                            rect.width + 2 * margin, rect.height + 2 * margin);
        for (Rectangle obs : obstacles) {
            if (expanded.intersects(obs)) {
                return true;
            }
        }
        return false;
    }

    private double distance(Rectangle r1, Rectangle r2) {
        double cx1 = r1.getCenterX(), cy1 = r1.getCenterY();
        double cx2 = r2.getCenterX(), cy2 = r2.getCenterY();
        return Math.hypot(cx1 - cx2, cy1 - cy2);
    }

    /**
     * 计算两个矩形之间的最近点对
     * @param a 矩形A（例如题目边界框）
     * @param b 矩形B（例如解析文本框）
     * @return 包含两个 Point2D 的数组，[0]为矩形A上的点，[1]为矩形B上的点
     */
    private Point2D[] closestPoints(Rectangle a, Rectangle b) {
        // 检查是否重叠（理论上不应发生，但以防万一）
        boolean overlapX = a.x < b.x + b.width && a.x + a.width > b.x;
        boolean overlapY = a.y < b.y + b.height && a.y + a.height > b.y;
        if (overlapX && overlapY) {
            // 重叠则返回中心连线
            return new Point2D[]{
                new Point2D.Double(a.getCenterX(), a.getCenterY()),
                new Point2D.Double(b.getCenterX(), b.getCenterY())
            };
        }

        // 判断相对方位
        boolean left = a.x + a.width <= b.x;
        boolean right = b.x + b.width <= a.x;
        boolean above = a.y + a.height <= b.y;
        boolean below = b.y + b.height <= a.y;

        double bestX1 = 0, bestY1 = 0, bestX2 = 0, bestY2 = 0;

        if (left) {
            // A在B左侧
            bestX1 = a.x + a.width;
            bestX2 = b.x;
            // 处理垂直方向
            if (a.y + a.height > b.y && a.y < b.y + b.height) {
                // Y方向有重叠，取重叠区域的中间Y
                double top = Math.max(a.y, b.y);
                double bottom = Math.min(a.y + a.height, b.y + b.height);
                double midY = (top + bottom) / 2.0;
                bestY1 = midY;
                bestY2 = midY;
            } else {
                // Y方向也分离
                if (a.y + a.height < b.y) {
                    // A在B上方
                    bestY1 = a.y + a.height;
                    bestY2 = b.y;
                } else {
                    // A在B下方
                    bestY1 = a.y;
                    bestY2 = b.y + b.height;
                }
            }
        } else if (right) {
            // A在B右侧
            bestX1 = a.x;
            bestX2 = b.x + b.width;
            if (a.y + a.height > b.y && a.y < b.y + b.height) {
                double top = Math.max(a.y, b.y);
                double bottom = Math.min(a.y + a.height, b.y + b.height);
                double midY = (top + bottom) / 2.0;
                bestY1 = midY;
                bestY2 = midY;
            } else {
                if (a.y + a.height < b.y) {
                    bestY1 = a.y + a.height;
                    bestY2 = b.y;
                } else {
                    bestY1 = a.y;
                    bestY2 = b.y + b.height;
                }
            }
        } else if (above) {
            // A在B上方
            bestY1 = a.y + a.height;
            bestY2 = b.y;
            if (a.x + a.width > b.x && a.x < b.x + b.width) {
                double leftX = Math.max(a.x, b.x);
                double rightX = Math.min(a.x + a.width, b.x + b.width);
                double midX = (leftX + rightX) / 2.0;
                bestX1 = midX;
                bestX2 = midX;
            } else {
                if (a.x + a.width < b.x) {
                    bestX1 = a.x + a.width;
                    bestX2 = b.x;
                } else {
                    bestX1 = a.x;
                    bestX2 = b.x + b.width;
                }
            }
        } else if (below) {
            // A在B下方
            bestY1 = a.y;
            bestY2 = b.y + b.height;
            if (a.x + a.width > b.x && a.x < b.x + b.width) {
                double leftX = Math.max(a.x, b.x);
                double rightX = Math.min(a.x + a.width, b.x + b.width);
                double midX = (leftX + rightX) / 2.0;
                bestX1 = midX;
                bestX2 = midX;
            } else {
                if (a.x + a.width < b.x) {
                    bestX1 = a.x + a.width;
                    bestX2 = b.x;
                } else {
                    bestX1 = a.x;
                    bestX2 = b.x + b.width;
                }
            }
        } else {
            // 理论上不会执行（因为已经处理了重叠和分离的情况）
            return new Point2D[]{
                new Point2D.Double(a.getCenterX(), a.getCenterY()),
                new Point2D.Double(b.getCenterX(), b.getCenterY())
            };
        }

        return new Point2D[]{
            new Point2D.Double(bestX1, bestY1),
            new Point2D.Double(bestX2, bestY2)
        };
    }

    /**
     * 将长文本按指定宽度拆分为多行
     */
    private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        // 如果文本本身包含换行符，先按换行符拆分
        String[] paragraphs = text.split("\n");
        for (String para : paragraphs) {
            if (para.isEmpty()) continue;
            // 按空格拆分单词（中文按字符，英文按空格）
            // 这里简单按字符逐字累加，适合中英文混合
            StringBuilder line = new StringBuilder();
            for (char c : para.toCharArray()) {
                String testLine = line.toString() + c;
                int width = fm.stringWidth(testLine);
                if (width > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(String.valueOf(c));
                } else {
                    line.append(c);
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private void drawMark(Graphics2D g, Rectangle bounds, String result) {
        int markSize = 28;
        Font font = new Font("微软雅黑", Font.BOLD, markSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        String mark = "正确".equals(result) ? "√" : "×";

        // 计算字符的宽度和高度（ ascent + descent 约为字符总高度）
        int charWidth = fm.stringWidth(mark);
        int charHeight = fm.getAscent() + fm.getDescent(); // 总高度

        // 计算居中坐标
        int x = bounds.x + (bounds.width - charWidth) / 2;
        // drawString 的 y 是基线位置，需加上 ascent 使字符垂直居中
        int y = bounds.y + (bounds.height - charHeight) / 2 + fm.getAscent();

        // 设置颜色
        if ("正确".equals(result)) {
            g.setColor(new Color(34, 197, 94)); // 绿色
        } else {
            g.setColor(new Color(220, 38, 38)); // 红色
        }

        g.drawString(mark, x, y);
    }

    private void drawUnderline(Graphics2D g, Rectangle bounds) {
        g.setColor(new Color(220, 38, 38));
        g.setStroke(new BasicStroke(3));
        int y = bounds.y + bounds.height + 4;
        g.drawLine(bounds.x, y, bounds.x + bounds.width, y);
    }

    /**
     * 绘制整体评价文本（在画布底部评价区域）
     */
    private void drawOverallComment(Graphics2D g, String comment, int canvasWidth, int canvasHeight, int origY, int origHeight) {
        if (comment == null || comment.isEmpty()) return;

        // 清洗评价文本，去除多余前缀
        comment = comment.replaceAll("【错题类型】[：:]*\\s*错题类型包括[：:]\\s*", "【错题类型】");
        comment = comment.replaceAll("【薄弱模块】[：:]*\\s*薄弱模块为[：:]\\s*", "【薄弱模块】");

        // 绘制评价区域背景
        int commentY = origY + origHeight + 20;
        g.setColor(new Color(240, 240, 240, 200));
        g.fillRect(0, commentY, canvasWidth, COMMENT_HEIGHT - 10);
        g.setColor(Color.GRAY);
        g.setStroke(new BasicStroke(1));
        g.drawLine(0, commentY, canvasWidth, commentY);

        // 绘制评价文本
        g.setFont(new Font("微软雅黑", Font.PLAIN, 16)); // 原为14
        g.setColor(Color.BLACK);
        int y = commentY + 20;
        int maxWidth = canvasWidth - 40;
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrapText(comment, fm, maxWidth);
        int lineHeight = fm.getHeight() + 2;
        for (String line : lines) {
            g.drawString(line, 20, y);
            y += lineHeight;
            if (y > commentY + COMMENT_HEIGHT - 30) break; // 防止超出评价区域
        }
    }
}
