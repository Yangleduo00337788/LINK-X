package com.linkx.server.controller;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 应用版本接口测试
 * <p>
 * 覆盖：
 * - 无 current：hasUpdate 必须为 false；
 * - current == latest：hasUpdate 必须为 false；
 * - current &lt; latest：hasUpdate 必须为 true；
 * - 数值段按数字比较（"1.10.0" &gt; "1.9.0"）；
 * - forceUpdate / 最低支持版本 / 灰度渠道。
 * </p>
 */
@DisplayName("应用版本接口测试")
class VersionControllerTest extends BaseIntegrationTest {

    @Autowired
    private LinkxProperties linkxProperties;

    @BeforeEach
    void seedAppVersionProps() {
        // 运行时配置表会覆盖 yml；用例内直接钉死内存属性，避免被其他 IT 污染
        LinkxProperties.App app = linkxProperties.getApp();
        app.setVersion("1.10.0");
        app.setChannel("beta");
        app.setReleaseNotes("新版本来了");
        app.setForceUpdate(true);
        app.setMinSupportedVersion("1.8.0");
        app.setSupportEmail("support@version.test");
        app.setSupportPhone("400-000-1111");
    }

    @Test
    @DisplayName("无 current 参数：hasUpdate=false")
    void noCurrent() throws Exception {
        mockMvc.perform(get("/app/version"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value("1.10.0"))
                .andExpect(jsonPath("$.data.hasUpdate").value(false))
                .andExpect(jsonPath("$.data.forceUpdate").value(false))
                .andExpect(jsonPath("$.data.channel").value("beta"))
                .andExpect(jsonPath("$.data.releaseNotes").value("当前已是最新版本"))
                .andExpect(jsonPath("$.data.supportEmail").value("support@version.test"))
                .andExpect(jsonPath("$.data.supportPhone").value("400-000-1111"));
    }

    @Test
    @DisplayName("current == latest：hasUpdate=false")
    void sameVersion() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "1.10.0").param("channel", "beta"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasUpdate").value(false))
                .andExpect(jsonPath("$.data.forceUpdate").value(false));
    }

    @Test
    @DisplayName("同渠道且 current < latest：hasUpdate=true 且 forceUpdate=true")
    void outdatedForce() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "1.9.5").param("channel", "beta"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value("1.10.0"))
                .andExpect(jsonPath("$.data.currentVersion").value("1.9.5"))
                .andExpect(jsonPath("$.data.hasUpdate").value(true))
                .andExpect(jsonPath("$.data.forceUpdate").value(true))
                .andExpect(jsonPath("$.data.releaseNotes").value("新版本来了"));
    }

    @Test
    @DisplayName("stable 客户端看不到 beta 灰度更新")
    void channelGrayFilter() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "1.9.5").param("channel", "stable"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasUpdate").value(false))
                .andExpect(jsonPath("$.data.forceUpdate").value(false));
    }

    @Test
    @DisplayName("低于最低支持版本：忽略灰度并强制升级")
    void belowMinSupported() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "1.7.0").param("channel", "stable"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasUpdate").value(true))
                .andExpect(jsonPath("$.data.forceUpdate").value(true));
    }

    @Test
    @DisplayName("current 大于 latest：hasUpdate=false（本地构建超过服务端基线）")
    void newerLocal() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "2.0.0").param("channel", "beta"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasUpdate").value(false));
    }
}
