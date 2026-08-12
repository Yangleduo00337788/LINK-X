package com.linkx.server.util;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.admin.SysAppVersion;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 客户端版本比较与发布渠道可见性判断。
 */
public final class AppVersionUtils {

    private AppVersionUtils() {
    }

    /**
     * 比较两个语义化版本号。
     *
     * @return 负数表示 a&lt;b，0 表示相等，正数表示 a&gt;b
     */
    public static int compare(String a, String b) {
        String[] aa = nullToEmpty(a).split("\\.");
        String[] bb = nullToEmpty(b).split("\\.");
        int len = Math.max(aa.length, bb.length);
        for (int i = 0; i < len; i++) {
            String x = i < aa.length ? aa[i] : "0";
            String y = i < bb.length ? bb[i] : "0";
            Integer xi = tryParseInt(x);
            Integer yi = tryParseInt(y);
            int cmp;
            if (xi != null && yi != null) {
                cmp = Integer.compare(xi, yi);
            } else {
                cmp = x.compareTo(y);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * 客户端是否可见该发布渠道的更新：
     * <ul>
     *   <li>未传客户端渠道：兼容旧客户端，一律可见</li>
     *   <li>发布渠道为 stable：全员可见</li>
     *   <li>发布渠道为 beta/dev：仅同渠道客户端可见</li>
     * </ul>
     */
    public static boolean isChannelEligible(String clientChannel, String releaseChannel) {
        if (!StringUtils.hasText(clientChannel)) {
            return true;
        }
        String release = normalizeChannel(releaseChannel);
        if ("stable".equals(release)) {
            return true;
        }
        return release.equals(normalizeChannel(clientChannel));
    }

    /**
     * 规范化客户端平台：windows / macos / linux。
     */
    public static String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return SysAppVersion.PLATFORM_WINDOWS;
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "win", "win32", "windows" -> SysAppVersion.PLATFORM_WINDOWS;
            case "mac", "darwin", "macos", "osx" -> SysAppVersion.PLATFORM_MACOS;
            case "linux", "appimage" -> SysAppVersion.PLATFORM_LINUX;
            default -> p;
        };
    }

    public static boolean isValidPlatform(String platform) {
        String p = normalizePlatform(platform);
        return SysAppVersion.PLATFORM_WINDOWS.equals(p)
                || SysAppVersion.PLATFORM_MACOS.equals(p)
                || SysAppVersion.PLATFORM_LINUX.equals(p);
    }

    public static String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "stable";
        }
        return channel.trim().toLowerCase();
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
