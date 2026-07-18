package com.linkx.server.controller;

import com.linkx.server.common.Result;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.AppVersionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用版本控制器：用于"检查更新"。
 * <p>
 * 路由：GET /app/version?current=1.0.0
 *  - 若客户端未传 current，则认为"未知"，永远返回 hasUpdate=false（避免误导）。
 *  - 版本比较采用"按 '.' 拆段 + 数字逐段比较"；位数不足视为 0。
 *  - 非数字段（如 "1.0.0-beta"）按字符串相等继续比较，避免抛错。
 * </p>
 */
@RestController
@RequestMapping("/app")
public class VersionController {

    private final LinkxProperties linkxProperties;

    public VersionController(LinkxProperties linkxProperties) {
        this.linkxProperties = linkxProperties;
    }

    @GetMapping("/version")
    public Result<AppVersionVO> checkVersion(@RequestParam(value = "current", required = false) String current) {
        LinkxProperties.App app = linkxProperties.getApp();
        String latest = nullToEmpty(app.getVersion());
        boolean hasUpdate = current != null && !current.isEmpty() && compareVersion(current, latest) < 0;
        return Result.success(AppVersionVO.builder()
                .version(latest)
                .currentVersion(current == null ? "" : current)
                .hasUpdate(hasUpdate)
                .forceUpdate(false)
                .channel(nullToEmpty(app.getChannel()))
                .releaseNotes(hasUpdate ? nullToEmpty(app.getReleaseNotes()) : "当前已是最新版本")
                .downloadUrl(nullToEmpty(app.getDownloadUrl()))
                .build());
    }

    /**
     * 比较两个语义化版本号。
     * @return 负数表示 a<b，0 表示相等，正数表示 a>b
     */
    private static int compareVersion(String a, String b) {
        String[] aa = a.split("\\.");
        String[] bb = b.split("\\.");
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
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}