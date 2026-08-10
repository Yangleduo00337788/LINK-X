package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.exception.CustomException;
import lombok.Builder;
import lombok.Value;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 滑块拼图验证码（经典样式：矩形 + 右侧凸起 + 顶部凹槽）。
 * 拼图块初始在左侧 x=0，用户拖到 targetX 与背景缺口对齐。
 */
final class SliderCaptchaRenderer {

    static final int WIDTH = 300;
    static final int HEIGHT = 150;
    static final int BLOCK_W = 50;
    static final int BLOCK_H = 50;
    static final int BUMP_R = 9;
    static final int SLIDER_TOLERANCE_PX = 5;

    private static final String SLIDER_PREFIX = "s:";

    private SliderCaptchaRenderer() {
    }

    @Value
    @Builder
    static class SliderAssets {
        String backgroundBase64;
        String puzzleBase64;
        int puzzleY;
        int targetX;
        String storedValue;
    }

    static SliderAssets generate(SecureRandom random) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g, random);
        } finally {
            g.dispose();
        }

        int targetX = 55 + random.nextInt(WIDTH - BLOCK_W - BUMP_R - 90);
        int targetY = 12 + random.nextInt(HEIGHT - BLOCK_H - BUMP_R - 12);

        Shape blockLocal = createPuzzleShape(0, 0);
        Rectangle bounds = blockLocal.getBounds();
        int pieceW = bounds.width;
        int pieceH = bounds.height;

        // 1) 拼图块：从原图裁切缺口区域
        BufferedImage puzzle = new BufferedImage(pieceW, pieceH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = puzzle.createGraphics();
        try {
            pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            pg.setComposite(AlphaComposite.Clear);
            pg.fillRect(0, 0, pieceW, pieceH);
            pg.setComposite(AlphaComposite.SrcOver);
            pg.translate(-bounds.x, -bounds.y);
            pg.setClip(blockLocal);
            pg.drawImage(canvas, -targetX, -targetY, null);
            pg.setClip(null);
            pg.setColor(new Color(255, 255, 255, 230));
            pg.setStroke(new BasicStroke(2f));
            pg.draw(blockLocal);
            pg.setColor(new Color(0, 0, 0, 60));
            pg.setStroke(new BasicStroke(1f));
            pg.draw(blockLocal);
        } finally {
            pg.dispose();
        }

        // 2) 背景：在 target 位置挖空并加遮罩
        Graphics2D bg = canvas.createGraphics();
        try {
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            bg.translate(targetX + bounds.x, targetY + bounds.y);
            Shape hole = blockLocal;
            bg.setColor(new Color(0, 0, 0, 140));
            bg.fill(hole);
            bg.setColor(new Color(255, 255, 255, 120));
            bg.setStroke(new BasicStroke(1.2f));
            bg.draw(hole);
        } finally {
            bg.dispose();
        }

        return SliderAssets.builder()
                .backgroundBase64(toDataUrl(canvas))
                .puzzleBase64(toDataUrl(puzzle))
                .puzzleY(targetY + bounds.y)
                .targetX(targetX + bounds.x)
                .storedValue(SLIDER_PREFIX + (targetX + bounds.x))
                .build();
    }

    static boolean isSliderValue(String stored) {
        return stored != null && stored.startsWith(SLIDER_PREFIX);
    }

    static boolean matchesSlider(String stored, String submitted) {
        if (!isSliderValue(stored)) {
            return false;
        }
        int target;
        int actual;
        try {
            target = Integer.parseInt(stored.substring(SLIDER_PREFIX.length()));
            actual = Integer.parseInt(submitted.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return Math.abs(target - actual) <= SLIDER_TOLERANCE_PX;
    }

    /**
     * 拼图轮廓：矩形 + 右侧圆形凸起 + 顶部圆形凹槽（Area 运算，稳定可靠）。
     */
    private static Shape createPuzzleShape(int x, int y) {
        Area area = new Area(new Rectangle2D.Double(x, y, BLOCK_W, BLOCK_H));
        // 右侧凸起
        area.add(new Area(new Ellipse2D.Double(
                x + BLOCK_W - BUMP_R, y + BLOCK_H / 2.0 - BUMP_R, BUMP_R * 2.0, BUMP_R * 2.0)));
        // 顶部凹槽
        area.subtract(new Area(new Ellipse2D.Double(
                x + BLOCK_W / 2.0 - BUMP_R, y - BUMP_R, BUMP_R * 2.0, BUMP_R * 2.0)));
        return area;
    }

    private static void paintBackground(Graphics2D g, SecureRandom random) {
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(60 + random.nextInt(50), 110 + random.nextInt(60), 170 + random.nextInt(50)),
                WIDTH, HEIGHT, new Color(35 + random.nextInt(40), 70 + random.nextInt(50), 130 + random.nextInt(40)));
        g.setPaint(gradient);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(255, 255, 255, 30 + random.nextInt(40)));
            g.fillRoundRect(random.nextInt(WIDTH - 40), random.nextInt(HEIGHT - 30),
                    30 + random.nextInt(70), 18 + random.nextInt(40), 10, 10);
        }
        for (int i = 0; i < 50; i++) {
            g.setColor(new Color(255, 255, 255, 50 + random.nextInt(80)));
            g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }
    }

    private static String toDataUrl(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new CustomException(500, "滑块验证码生成失败");
        }
    }
}
