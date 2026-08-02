package com.linkx.server.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminFeedbackService;
import com.linkx.server.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 首批能力回归：仪表盘待办/SLA 字段、举报筛选、反馈超时筛选、登录/风险日志 IP 归属地。
 */
@DisplayName("管理端 M6 能力")
class AdminM6FeaturesIT extends BaseIntegrationTest {

    private static final long ADMIN_ROLE = 1001L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private RbacService rbacService;
    @Autowired
    private AdminFeedbackService adminFeedbackService;
    @Autowired
    private FeedbackMapper feedbackMapper;
    @Autowired
    private SysRiskEventMapper sysRiskEventMapper;

    @Test
    @DisplayName("仪表盘摘要含逾期反馈与待处理举报字段")
    void dashboardSummaryIncludesSlaAndReportCounters() throws Exception {
        TestUser admin = adminUser("m6sum");

        mockMvc.perform(get("/admin/dashboard/summary").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.overdueFeedback").exists())
                .andExpect(jsonPath("$.data.pendingReports").exists())
                .andExpect(jsonPath("$.data.pendingFeedback").exists())
                .andExpect(jsonPath("$.data.pendingReviews").exists());
    }

    @Test
    @DisplayName("仪表盘待办列表可访问且结构合法")
    void dashboardPendingTasksShape() throws Exception {
        TestUser admin = adminUser("m6pend");

        MvcResult result = mockMvc.perform(get("/admin/dashboard/pending-tasks")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode item : items) {
            assertTrue(item.hasNonNull("type"));
            assertTrue(item.hasNonNull("title"));
            assertTrue(item.has("count"));
            assertTrue(item.hasNonNull("path"));
            assertTrue(item.path("path").asText().startsWith("/admin/"));
        }
    }

    @Test
    @DisplayName("审核列表支持 sourceType=report 举报筛选")
    void reviewsCanFilterByReportSource() throws Exception {
        TestUser admin = adminUser("m6rep");

        mockMvc.perform(get("/admin/reviews")
                        .param("page", "1")
                        .param("size", "20")
                        .param("sourceType", "report")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").exists());
    }

    @Test
    @DisplayName("反馈 SLA：写入逾期样本后 countOverdue/摘要计数递增")
    void feedbackOverdueCountReflectsPendingPastSla() throws Exception {
        TestUser admin = adminUser("m6fb");
        long before = adminFeedbackService.countOverdue();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -3);
        Date old = cal.getTime();
        Feedback row = Feedback.builder()
                .userId(admin.userId)
                .username(admin.username)
                .type("bug")
                .content("m6 overdue sample")
                .status("pending")
                .build();
        feedbackMapper.insert(row);
        // onInsertValue=NOW() 会覆盖创建时间，再回写为逾期末尾
        row.setCreateTime(old);
        feedbackMapper.update(row);

        long after = adminFeedbackService.countOverdue();
        assertTrue(after >= before + 1, "overdue count should increase after inserting stale pending feedback");

        mockMvc.perform(get("/admin/dashboard/summary").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.overdueFeedback").value((int) after));
    }

    @Test
    @DisplayName("登录日志返回 region 归属地字段")
    void loginLogsExposeRegion() throws Exception {
        TestUser admin = adminUser("m6ll");

        MvcResult result = mockMvc.perform(get("/admin/login-logs")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items");
        assertTrue(items.isArray());
        // 至少应有本次注册/登录产生的审计；每条需带 region 键（值可为内网文案）
        assertTrue(items.size() > 0, "expected at least one login log");
        for (JsonNode item : items) {
            assertTrue(item.has("region"), "login log missing region field: " + item);
        }
    }

    @Test
    @DisplayName("风险事件详情返回 region 归属地字段")
    void riskEventDetailExposesRegion() throws Exception {
        // 列表查询在 H2 上存在方言差异，详情路径覆盖 region 字段即可
        TestUser admin = adminUser("m6re");
        Date now = new Date();
        SysRiskEvent event = SysRiskEvent.builder()
                .eventType(SysRiskEvent.TYPE_RATE_LIMIT)
                .title("m6 rate limit")
                .detail("it")
                .riskLevel("medium")
                .status(SysRiskEvent.STATUS_PENDING)
                .userId(admin.userId)
                .username(admin.username)
                .ip("127.0.0.1")
                .createTime(now)
                .updateTime(now)
                .build();
        sysRiskEventMapper.insert(event);

        mockMvc.perform(get("/admin/risk-events/{id}", event.getId())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(event.getId()))
                .andExpect(jsonPath("$.data.region").exists());
    }

    private TestUser adminUser(String prefix) {
        TestUser user = registerAndLogin(prefix);
        sysUserRoleMapper.insert(SysUserRole.builder()
                .userId(user.userId)
                .roleId(ADMIN_ROLE)
                .createBy(null)
                .deleted(0)
                .build());
        rbacService.evictUserCache(user.userId);
        return login(user.username, "Test1234abcd");
    }
}
