package com.linkx.server.controller;

import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 应用版本接口测试
 * <p>
 * 覆盖：
 * - 无 current：hasUpdate 必须为 false；
 * - current == latest：hasUpdate 必须为 false；
 * - current < latest：hasUpdate 必须为 true；
 * - 数值段按数字比较（"1.10.0" > "1.9.0"）。
 * </p>
 */
@DisplayName("应用版本接口测试")
@TestPropertySource(properties = {
        "linkx.app.version=1.10.0",
        "linkx.app.channel=stable",
        "linkx.app.release-notes=新版本来了"
})
class VersionControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("无 current 参数：hasUpdate=false")
    void noCurrent() throws Exception {
        mockMvc.perform(get("/app/version"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value("1.10.0"))
                .andExpect(jsonPath("$.data.hasUpdate").value(false))
                .andExpect(jsonPath("$.data.forceUpdate").value(false))
                .andExpect(jsonPath("$.data.channel").value("stable"))
                .andExpect(jsonPath("$.data.releaseNotes").value("当前已是最新版本"));
    }

    @Test
    @DisplayName("current == latest：hasUpdate=false")
    void sameVersion() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "1.10.0"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasUpdate").value(false));
    }

    @Test
    @DisplayName("current < latest：hasUpdate=true，且 releaseNotes 透传")
    void outdated() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "1.9.5"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value("1.10.0"))
                .andExpect(jsonPath("$.data.currentVersion").value("1.9.5"))
                .andExpect(jsonPath("$.data.hasUpdate").value(true))
                .andExpect(jsonPath("$.data.releaseNotes").value("新版本来了"));
    }

    @Test
    @DisplayName("current 大于 latest：hasUpdate=false（本地构建超过服务端基线）")
    void newerLocal() throws Exception {
        mockMvc.perform(get("/app/version").param("current", "2.0.0"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasUpdate").value(false));
    }
}