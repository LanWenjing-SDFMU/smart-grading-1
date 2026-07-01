package com.example.smartgrading.service;

import com.example.smartgrading.model.UnifiedGradingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ImageMarkService {

    /**
     * 根据统一批改结果绘制批注（每个题目自带坐标和判断结果）
     */
    public byte[] markImageWithUnifiedResult(MultipartFile file, UnifiedGradingResult result) throws IOException {
        log.info("=== 开始绘制批注 ===");
        log.info("图片文件名：{}", file.getOriginalFilename());

        BufferedImage original = ImageIO.read(file.getInputStream());
        int imgWidth = original.getWidth();
        int imgHeight = original.getHeight();
        log.info("图片尺寸：宽={}, 高={}", imgWidth, imgHeight);

        // 检查 questions 是否为空
        if (result.getQuestions() == null || result.getQuestions().isEmpty()) {
            log.warn("questions 为空，无法绘制！");
            return file.getBytes();  // 返回原图
        }
        log.info("共有 {} 道题需要绘制", result.getQuestions().size());

        BufferedImage marked = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = marked.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, null);

        for (UnifiedGradingResult.QuestionResult q : result.getQuestions()) {
            UnifiedGradingResult.BoundingBox bbox = q.getBbox();
            if (bbox == null) {
                log.warn("题目没有 bbox，跳过绘制");
                continue;
            }
            log.info("原始 bbox：x={}, y={}, width={}, height={}", bbox.getX(), bbox.getY(), bbox.getWidth(), bbox.getHeight());

            // 坐标有效性检查：如果坐标超出图片范围，进行调整
            int x = bbox.getX();
            int y = bbox.getY();
            int w = bbox.getWidth();
            int h = bbox.getHeight();

            // 如果坐标是归一化的（0~1000），需要缩放
            if (x <= 1000 && y <= 1000 && w <= 1000 && h <= 1000) {
                x = (int) (x * imgWidth / 1000.0);
                y = (int) (y * imgHeight / 1000.0);
                w = (int) (w * imgWidth / 1000.0);
                h = (int) (h * imgHeight / 1000.0);
                log.info("缩放后的 bbox：x={}, y={}, width={}, height={}", x, y, w, h);
            }

            // 确保坐标在图片范围内
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (x + w > imgWidth) w = imgWidth - x;
            if (y + h > imgHeight) h = imgHeight - y;
            if (w <= 0 || h <= 0) {
                log.warn("调整后宽高无效，跳过绘制");
                continue;
            }

            Rectangle bounds = new Rectangle(x, y, w, h);
            log.info("实际绘制矩形：{}", bounds);

            // 绘制对错号
            drawMark(g, bounds, q.getResult());
            // 如果错误，下划线
            if ("错误".equals(q.getResult())) {
                drawUnderline(g, bounds);
            }
            // 绘制解析和错因
            drawUnifiedAnnotations(g, bounds, q, imgWidth, imgHeight);
            log.info("完成一道题的绘制");
        }

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(marked, "png", baos);
        log.info("绘制完成，图片大小：{} 字节", baos.size());
        return baos.toByteArray();
    }

    /**
     * 绘制统一批注（解析 + 错因）
     */
    private void drawUnifiedAnnotations(Graphics2D g, Rectangle bounds,
                                        UnifiedGradingResult.QuestionResult q,
                                        int imgWidth, int imgHeight) {
        int fontSize = 14; // 适当大小
        Font font = new Font("微软雅黑", Font.PLAIN, fontSize);
        g.setFont(font);
        g.setColor(Color.RED);

        // 准备要显示的文本
        String explanation = q.getExplanation();
        String errorText = "【错因】" + q.getErrorAnalysis();
        boolean hasError = "错误".equals(q.getResult()) && q.getErrorAnalysis() != null && !q.getErrorAnalysis().isEmpty();

        // 计算可用宽度（右侧区域，如果右侧不够则放在下方）
        int padding = 10;
        int maxWidth = Math.min(400, imgWidth - bounds.x - bounds.width - padding); // 右侧最大宽度
        int x = bounds.x + bounds.width + padding;
        int y = bounds.y + 20;

        // 如果右侧空间不足，放到答案下方
        if (maxWidth < 100 || x + 100 > imgWidth) {
            maxWidth = Math.min(500, imgWidth - bounds.x - padding);
            x = bounds.x + padding;
            y = bounds.y + bounds.height + 30;
            // 如果下方也不够，则放在右侧（但这种情况很少）
        }

        // 绘制解析（自动换行）
        int lineHeight = g.getFontMetrics().getHeight() + 2;
        List<String> lines = wrapText(explanation, g.getFontMetrics(), maxWidth);
        for (String line : lines) {
            if (y + lineHeight < imgHeight - 10) { // 确保不超出图片底部
                g.drawString(line, x, y);
                y += lineHeight;
            } else {
                // 超出图片底部，尝试往上调整（但通常不会）
                break;
            }
        }

        // 如果有错因，换行绘制错因（红色）
        if (hasError) {
            y += 6; // 空行间距
            g.setColor(Color.RED);
            lines = wrapText(errorText, g.getFontMetrics(), maxWidth);
            for (String line : lines) {
                if (y + lineHeight < imgHeight - 10) {
                    g.drawString(line, x, y);
                    y += lineHeight;
                } else {
                    break;
                }
            }
        }
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
        // 使用支持中文的字体
        Font font = new Font("微软雅黑", Font.BOLD, markSize);
        g.setFont(font);
        
        // 计算绘制位置：答案区域的右上角外侧
        int x = bounds.x + bounds.width + 10;
        int y = bounds.y - 4;
        
        if ("正确".equals(result)) {
            g.setColor(new Color(34, 197, 94)); // 绿色
            g.drawString("√", x, y);
        } else {
            g.setColor(new Color(220, 38, 38)); // 红色
            g.drawString("×", x, y);
        }
    }

    private void drawUnderline(Graphics2D g, Rectangle bounds) {
        g.setColor(new Color(220, 38, 38));
        g.setStroke(new BasicStroke(3));
        int y = bounds.y + bounds.height + 4;
        g.drawLine(bounds.x, y, bounds.x + bounds.width, y);
    }
}
