package com.linkx.server.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.admin.AdminCsvResponses;
import com.linkx.server.common.admin.AdminExportModule;
import com.linkx.server.controller.admin.dto.AdminAuditLogQueryDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistQueryDTO;
import com.linkx.server.controller.admin.dto.AdminDeviceQueryDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackQueryDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.dto.AdminReviewQueryDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventQueryDTO;
import com.linkx.server.controller.admin.dto.AdminUserQueryDTO;
import com.linkx.server.controller.admin.vo.AdminBlacklistVO;
import com.linkx.server.controller.admin.vo.AdminDeviceVO;
import com.linkx.server.controller.admin.vo.AdminFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminLoginLogVO;
import com.linkx.server.controller.admin.vo.AdminOperationLogVO;
import com.linkx.server.controller.admin.vo.AdminReviewVO;
import com.linkx.server.controller.admin.vo.AdminRiskEventVO;
import com.linkx.server.controller.admin.vo.AdminStatisticContentVO;
import com.linkx.server.controller.admin.vo.AdminStatisticFeedbackVO;
import com.linkx.server.controller.admin.vo.AdminStatisticOverviewVO;
import com.linkx.server.controller.admin.vo.AdminStatisticRiskVO;
import com.linkx.server.controller.admin.vo.AdminStatisticUserVO;
import com.linkx.server.controller.admin.vo.AdminUserListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将各模块 listForExport 结果组装为 CSV 字节（同步/异步共用）。
 */
@Component
@RequiredArgsConstructor
public class AdminExportCsvBuilder {

    private final ObjectMapper objectMapper;
    private final AdminUserService adminUserService;
    private final AdminDeviceService adminDeviceService;
    private final AdminBlacklistService adminBlacklistService;
    private final AdminRiskEventService adminRiskEventService;
    private final AdminReviewService adminReviewService;
    private final AdminFeedbackService adminFeedbackService;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminStatisticsService adminStatisticsService;

    public record CsvPayload(byte[] bytes, String fileName, int rowCount) {
    }

    public CsvPayload build(AdminExportModule module, String queryJson) {
        Map<?, ?> queryMap = parseQuery(queryJson);
        return switch (module) {
            case USERS -> buildUsers(convert(queryMap, AdminUserQueryDTO.class), module);
            case DEVICES -> buildDevices(convert(queryMap, AdminDeviceQueryDTO.class), module);
            case BLACKLIST -> buildBlacklist(convert(queryMap, AdminBlacklistQueryDTO.class), module);
            case RISK_EVENTS -> buildRiskEvents(convert(queryMap, AdminRiskEventQueryDTO.class), module);
            case REVIEWS -> buildReviews(convert(queryMap, AdminReviewQueryDTO.class), module);
            case FEEDBACK -> buildFeedback(convert(queryMap, AdminFeedbackQueryDTO.class), module);
            case AUDIT_LOGS -> buildAuditLogs(convert(queryMap, AdminAuditLogQueryDTO.class), module);
            case LOGIN_LOGS -> buildLoginLogs(convert(queryMap, AdminPageQueryDTO.class), module);
            case STATISTICS -> buildStatistics(queryMap, module);
        };
    }

    private CsvPayload buildUsers(AdminUserQueryDTO query, AdminExportModule module) {
        List<AdminUserListVO> items = adminUserService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminUserListVO item : items) {
            String roles = item.getRoles() == null ? "" : item.getRoles().stream().collect(Collectors.joining("|"));
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getNickname()),
                    AdminCsvResponses.cell(item.getEmail()),
                    AdminCsvResponses.cell(item.getPhone()),
                    AdminCsvResponses.cell(item.getStatus()),
                    roles,
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return payload(module, List.of("id", "username", "nickname", "email", "phone", "status", "roles", "createTime"), rows);
    }

    private CsvPayload buildDevices(AdminDeviceQueryDTO query, AdminExportModule module) {
        List<AdminDeviceVO> items = adminDeviceService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminDeviceVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getNickname()),
                    AdminCsvResponses.cell(item.getDeviceId()),
                    AdminCsvResponses.cell(item.getDeviceName()),
                    AdminCsvResponses.cell(item.getDeviceType()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(Boolean.TRUE.equals(item.getOnline()) ? "online" : "offline"),
                    AdminCsvResponses.cell(item.getLastActive()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return payload(module, List.of("id", "userId", "username", "nickname", "deviceId", "deviceName",
                "deviceType", "ip", "online", "lastActive", "createTime"), rows);
    }

    private CsvPayload buildBlacklist(AdminBlacklistQueryDTO query, AdminExportModule module) {
        List<AdminBlacklistVO> items = adminBlacklistService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminBlacklistVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getNickname()),
                    AdminCsvResponses.cell(item.getReason()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getCreatedByName()),
                    AdminCsvResponses.cell(item.getCreateTime()),
                    AdminCsvResponses.cell(item.getReleasedByName()),
                    AdminCsvResponses.cell(item.getReleasedAt()),
                    AdminCsvResponses.cell(item.getReleaseReason()),
            });
        }
        return payload(module, List.of("id", "userId", "username", "nickname", "reason", "status",
                "createdBy", "createTime", "releasedBy", "releasedAt", "releaseReason"), rows);
    }

    private CsvPayload buildRiskEvents(AdminRiskEventQueryDTO query, AdminExportModule module) {
        List<AdminRiskEventVO> items = adminRiskEventService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminRiskEventVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getEventType()),
                    AdminCsvResponses.cell(item.getTitle()),
                    AdminCsvResponses.cell(item.getRiskLevel()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(item.getResolution()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return payload(module, List.of("id", "eventType", "title", "riskLevel", "status", "username", "ip", "resolution", "createTime"), rows);
    }

    private CsvPayload buildReviews(AdminReviewQueryDTO query, AdminExportModule module) {
        List<AdminReviewVO> items = adminReviewService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminReviewVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getSourceType()),
                    AdminCsvResponses.cell(item.getTitle()),
                    AdminCsvResponses.cell(item.getReporterUsername()),
                    AdminCsvResponses.cell(item.getTargetId()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getRiskLevel()),
                    AdminCsvResponses.cell(item.getResolution()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return payload(module, List.of("id", "sourceType", "title", "reporter", "targetId", "status", "riskLevel", "resolution", "createTime"), rows);
    }

    private CsvPayload buildFeedback(AdminFeedbackQueryDTO query, AdminExportModule module) {
        List<AdminFeedbackVO> items = adminFeedbackService.listForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminFeedbackVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getType()),
                    AdminCsvResponses.cell(item.getContent()),
                    AdminCsvResponses.cell(item.getContact()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getReply()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return payload(module, List.of("id", "userId", "username", "type", "content", "contact", "status", "reply", "createTime"), rows);
    }

    private CsvPayload buildAuditLogs(AdminAuditLogQueryDTO query, AdminExportModule module) {
        List<AdminOperationLogVO> items = adminAuditLogService.listAuditLogsForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminOperationLogVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getOperationType()),
                    AdminCsvResponses.cell(item.getDescription()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getTargetUsername()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(item.getStatus()),
                    AdminCsvResponses.cell(item.getFailureReason()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return payload(module, List.of("id", "operationType", "description", "username", "targetUsername", "ip", "status", "failureReason", "createTime"), rows);
    }

    private CsvPayload buildLoginLogs(AdminPageQueryDTO query, AdminExportModule module) {
        List<AdminLoginLogVO> items = adminAuditLogService.listLoginLogsForExport(query);
        List<String[]> rows = new ArrayList<>(items.size());
        for (AdminLoginLogVO item : items) {
            rows.add(new String[]{
                    AdminCsvResponses.cell(item.getId()),
                    AdminCsvResponses.cell(item.getUserId()),
                    AdminCsvResponses.cell(item.getUsername()),
                    AdminCsvResponses.cell(item.getIp()),
                    AdminCsvResponses.cell(item.getUserAgent()),
                    AdminCsvResponses.cell(item.getSuccess()),
                    AdminCsvResponses.cell(item.getReason()),
                    AdminCsvResponses.cell(item.getCreateTime()),
            });
        }
        return payload(module, List.of("id", "userId", "username", "ip", "userAgent", "success", "reason", "createTime"), rows);
    }

    private CsvPayload buildStatistics(Map<?, ?> queryMap, AdminExportModule module) {
        int days = 14;
        if (queryMap != null && queryMap.get("days") != null) {
            try {
                days = Integer.parseInt(String.valueOf(queryMap.get("days")));
            } catch (NumberFormatException ignored) {
                days = 14;
            }
        }
        days = Math.max(7, Math.min(90, days));

        AdminStatisticOverviewVO ov = adminStatisticsService.overview(days);
        AdminStatisticUserVO users = adminStatisticsService.users(days);
        AdminStatisticContentVO content = adminStatisticsService.content(days);
        AdminStatisticRiskVO risk = adminStatisticsService.risk(days);
        AdminStatisticFeedbackVO feedback = adminStatisticsService.feedback(days);

        List<String[]> rows = new ArrayList<>();
        rows.add(kv("days", days));
        rows.add(kv("totalUsers", ov.getTotalUsers()));
        rows.add(kv("activeUsers", ov.getActiveUsers()));
        rows.add(kv("onlineDevices", ov.getOnlineDevices()));
        rows.add(kv("pendingFeedback", ov.getPendingFeedback()));
        rows.add(kv("pendingReviews", ov.getPendingReviews()));
        rows.add(kv("riskEvents", ov.getRiskEvents()));
        rows.add(kv("todayNewUsers", ov.getTodayNewUsers()));
        rows.add(kv("todayMessages", ov.getTodayMessages()));
        rows.add(kv("todayLogins", ov.getTodayLogins()));
        rows.add(kv("totalMessages", ov.getTotalMessages()));
        rows.add(kv("totalUploads", ov.getTotalUploads()));
        rows.add(kv("closedFeedback", ov.getClosedFeedback()));
        rows.add(kv("newUsersInRange", users.getNewUsersInRange()));
        rows.add(kv("loginSuccessInRange", users.getLoginSuccessInRange()));
        rows.add(kv("loginFailInRange", users.getLoginFailInRange()));
        rows.add(kv("messagesInRange", content.getMessagesInRange()));
        rows.add(kv("momentsInRange", content.getMomentsInRange()));
        rows.add(kv("uploadsInRange", content.getUploadsInRange()));
        rows.add(kv("sensitiveHitsInRange", risk.getSensitiveHitsInRange()));
        rows.add(kv("messageStormsInRange", risk.getMessageStormsInRange()));
        rows.add(kv("loginLocksInRange", risk.getLoginLocksInRange()));
        rows.add(kv("rateLimitsInRange", risk.getRateLimitsInRange()));
        rows.add(kv("createdFeedbackInRange", feedback.getCreatedInRange()));
        rows.add(kv("repliedFeedbackInRange", feedback.getRepliedInRange()));
        rows.add(kv("closedFeedbackInRange", feedback.getClosedInRange()));
        return payload(module, List.of("metric", "value"), rows);
    }

    private CsvPayload payload(AdminExportModule module, List<String> headers, List<String[]> rows) {
        return new CsvPayload(
                AdminCsvResponses.toBytes(headers, rows),
                AdminCsvResponses.buildFilename(module.getFilenamePrefix()),
                rows == null ? 0 : rows.size()
        );
    }

    private static String[] kv(String key, Object value) {
        return new String[]{key, AdminCsvResponses.cell(value)};
    }

    private Map<?, ?> parseQuery(String queryJson) {
        if (!StringUtils.hasText(queryJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(queryJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private <T> T convert(Map<?, ?> queryMap, Class<T> type) {
        if (queryMap == null || queryMap.isEmpty()) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("cannot create " + type.getSimpleName(), e);
            }
        }
        return objectMapper.convertValue(queryMap, type);
    }
}
