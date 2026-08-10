package com.linkx.server.service.admin.rule;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.entity.admin.SysDutySchedule;
import com.linkx.server.entity.admin.SysDutyScheduleSlot;
import com.linkx.server.entity.admin.SysFeedbackDispatchRule;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.admin.SysDutyScheduleMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleSlotMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FeedbackAssigneeResolver {

    private static final String ROUND_ROBIN_KEY_PREFIX = "linkx:feedback:rr:";

    private final SysDutyScheduleMapper dutyScheduleMapper;
    private final SysDutyScheduleSlotMapper dutyScheduleSlotMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<Long> resolve(SysFeedbackDispatchRule rule) {
        if (rule == null) {
            return Optional.empty();
        }
        String source = normalizeSource(rule.getAssigneeSource());
        return switch (source) {
            case "duty" -> resolveFromDuty(rule.getDutyScheduleId());
            case "round_robin" -> resolveFromRoundRobin(rule);
            default -> Optional.ofNullable(rule.getAssigneeId());
        };
    }

    private Optional<Long> resolveFromDuty(Long scheduleId) {
        if (scheduleId == null) {
            return Optional.empty();
        }
        SysDutySchedule schedule = dutyScheduleMapper.selectOneById(scheduleId);
        if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())
                || !Boolean.TRUE.equals(schedule.getEnabled())) {
            return Optional.empty();
        }
        ZoneId zoneId = resolveZone(schedule.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        int weekday = now.getDayOfWeek().getValue();
        LocalTime current = now.toLocalTime();

        List<SysDutyScheduleSlot> slots = dutyScheduleSlotMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysDutyScheduleSlot::getScheduleId).eq(scheduleId)
                        .and(SysDutyScheduleSlot::getDeleted).eq(0)
                        .and(SysDutyScheduleSlot::getWeekday).eq(weekday)
                        .orderBy(SysDutyScheduleSlot::getSortOrder, true)
                        .orderBy(SysDutyScheduleSlot::getId, true));
        for (SysDutyScheduleSlot slot : slots) {
            if (slot.getStartTime() == null || slot.getEndTime() == null || slot.getAssigneeId() == null) {
                continue;
            }
            LocalTime start = slot.getStartTime().toLocalTime();
            LocalTime end = slot.getEndTime().toLocalTime();
            if (isWithinSlot(current, start, end)) {
                return Optional.of(slot.getAssigneeId());
            }
        }
        return Optional.empty();
    }

    private Optional<Long> resolveFromRoundRobin(SysFeedbackDispatchRule rule) {
        List<Long> pool = parseAssigneePool(rule.getActionConfig());
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        if (pool.size() == 1) {
            return Optional.of(pool.get(0));
        }
        String key = ROUND_ROBIN_KEY_PREFIX + rule.getId();
        Long index = redisTemplate.opsForValue().increment(key);
        if (index == null) {
            index = 0L;
        }
        int pos = (int) (Math.floorMod(index - 1, pool.size()));
        return Optional.of(pool.get(pos));
    }

    List<Long> parseAssigneePool(String actionConfig) {
        if (!StringUtils.hasText(actionConfig)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(actionConfig);
            JsonNode ids = root.get("assigneeIds");
            if (ids == null || !ids.isArray()) {
                return List.of();
            }
            List<Long> pool = new ArrayList<>();
            for (JsonNode idNode : ids) {
                Long parsed = parseAssigneeId(idNode);
                if (parsed != null) {
                    pool.add(parsed);
                }
            }
            pool.sort(Comparator.naturalOrder());
            return pool;
        } catch (Exception e) {
            return List.of();
        }
    }

    static boolean isWithinSlot(LocalTime current, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (end.isAfter(start)) {
            return !current.isBefore(start) && current.isBefore(end);
        }
        return !current.isBefore(start) || current.isBefore(end);
    }

    private static ZoneId resolveZone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    static String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "fixed";
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    public void validateRuleAssignee(SysFeedbackDispatchRule rule) {
        if (!needsAssignee(rule)) {
            return;
        }
        String source = normalizeSource(rule.getAssigneeSource());
        switch (source) {
            case "duty" -> {
                if (rule.getDutyScheduleId() == null) {
                    throw new CustomException(400, "duty schedule required");
                }
            }
            case "round_robin" -> {
                if (parseAssigneePool(rule.getActionConfig()).isEmpty()) {
                    throw new CustomException(400, "round robin pool required");
                }
            }
            default -> {
                if (rule.getAssigneeId() == null) {
                    throw new CustomException(400, "assignee required");
                }
            }
        }
    }

    private static boolean needsAssignee(SysFeedbackDispatchRule rule) {
        if (rule == null) {
            return false;
        }
        String actionType = rule.getActionType();
        if (!StringUtils.hasText(actionType)) {
            return true;
        }
        String normalized = actionType.trim().toLowerCase(Locale.ROOT);
        return "assign".equals(normalized) || "assign_notify".equals(normalized);
    }

    private static Long parseAssigneeId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isIntegralNumber()) {
            return idNode.longValue();
        }
        if (idNode.isTextual()) {
            String text = idNode.asText().trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (idNode.canConvertToLong()) {
            return idNode.longValue();
        }
        return null;
    }
}
