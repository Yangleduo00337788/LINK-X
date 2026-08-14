package com.linkx.server.service.linkmate;


/**
 * 作者：yangleduo
 */
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 根据模型名判断灵伴是否支持「深度思考」及推理模型解析。
 */
public final class LinkMateModelCapability {

    private static final Pattern REASONING_MODEL_PATTERN = Pattern.compile(
            "(?i)(reasoner|thinking|deep-think|deepthink|\\br1\\b|o1|o3)"
    );

    private LinkMateModelCapability() {
    }

  /**
   * 当前配置模型是否可向用户开放深度思考开关。
   */
    public static boolean supportsDeepThinking(String model) {
        if (!StringUtils.hasText(model)) {
            return false;
        }
        String normalized = model.trim().toLowerCase(Locale.ROOT);
        if (REASONING_MODEL_PATTERN.matcher(normalized).find()) {
            return true;
        }
        return "deepseek-chat".equals(normalized);
    }

  /**
   * 深度思考开启时实际调用的模型名。
   */
    public static String resolveModel(String configuredModel, boolean deepThinking) {
        if (!StringUtils.hasText(configuredModel)) {
            return configuredModel;
        }
        String trimmed = configuredModel.trim();
        if (!deepThinking) {
            return trimmed;
        }
        if (REASONING_MODEL_PATTERN.matcher(trimmed).find()) {
            return trimmed;
        }
        if ("deepseek-chat".equalsIgnoreCase(trimmed)) {
            return "deepseek-reasoner";
        }
        return trimmed;
    }
}
