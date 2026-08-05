package com.linkx.server.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SliderCaptchaRenderer 单元测试")
class SliderCaptchaRendererTest {

    @RepeatedTest(5)
    @DisplayName("拼图块应包含足够的不透明像素")
    void puzzle_hasOpaquePixels() throws Exception {
        SliderCaptchaRenderer.SliderAssets assets =
                SliderCaptchaRenderer.generate(new SecureRandom());

        BufferedImage puzzle = decodeBase64Image(assets.getPuzzleBase64());
        int opaque = 0;
        for (int y = 0; y < puzzle.getHeight(); y++) {
            for (int x = 0; x < puzzle.getWidth(); x++) {
                if ((puzzle.getRGB(x, y) >>> 24) > 128) {
                    opaque++;
                }
            }
        }
        int total = puzzle.getWidth() * puzzle.getHeight();
        assertTrue(opaque > total * 0.15,
                "拼图不透明像素过少: " + opaque + "/" + total);
    }

    @Test
    @DisplayName("滑块偏移校验应在容差内通过")
    void matchesSlider_withinTolerance() {
        assertTrue(SliderCaptchaRenderer.matchesSlider("s:120", "118"));
        assertTrue(SliderCaptchaRenderer.matchesSlider("s:120", "125"));
        assertFalse(SliderCaptchaRenderer.matchesSlider("s:120", "110"));
        assertFalse(SliderCaptchaRenderer.matchesSlider("abcd", "120"));
    }

    private static BufferedImage decodeBase64Image(String dataUrl) throws Exception {
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
