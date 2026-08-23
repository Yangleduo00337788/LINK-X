package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.AdminKeywordQuery;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminDutyScheduleDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminDutyScheduleVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysDutySchedule;
import com.linkx.server.entity.admin.SysDutyScheduleSlot;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleMapper;
import com.linkx.server.mapper.admin.SysDutyScheduleSlotMapper;
import com.linkx.server.service.admin.AdminDutyScheduleService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Time;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDutyScheduleServiceImpl implements AdminDutyScheduleService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final SysDutyScheduleMapper dutyScheduleMapper;
    private final SysDutyScheduleSlotMapper dutyScheduleSlotMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public PageResultVO<AdminDutyScheduleVO> list(AdminPageQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create().where(SysDutySchedule::getDeleted).eq(0);
        String kw = AdminKeywordQuery.forLike(query.getKeyword());
        if (kw != null) {
            qw.and(SysDutySchedule::getName).like(kw);
        }
        if (query.getStatus() != null) {
            qw.and(SysDutySchedule::getEnabled).eq(query.getStatus() == 1);
        }
        qw.orderBy(SysDutySchedule::getUpdateTime, false);
        long total = dutyScheduleMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminDutyScheduleVO> items = dutyScheduleMapper.selectListByQuery(qw).stream()
                .map(schedule -> toVO(schedule, false))
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminDutyScheduleVO detail(Long id) {
        return toVO(requireSchedule(id), true);
    }

    @Override
    @Transactional
    public AdminDutyScheduleVO create(AdminDutyScheduleDTO dto, Long operatorId) {
        Date now = new Date();
        SysDutySchedule schedule = SysDutySchedule.builder()
                .name(normalizeName(dto.getName()))
                .description(normalizeOptional(dto.getDescription()))
                .timezone(normalizeTimezone(dto.getTimezone()))
                .enabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()))
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        dutyScheduleMapper.insert(schedule);
        replaceSlots(schedule.getId(), dto.getSlots());
        return detail(schedule.getId());
    }

    @Override
    @Transactional
    public AdminDutyScheduleVO update(Long id, AdminDutyScheduleDTO dto, Long operatorId) {
        SysDutySchedule schedule = requireSchedule(id);
        schedule.setName(normalizeName(dto.getName()));
        schedule.setDescription(normalizeOptional(dto.getDescription()));
        schedule.setTimezone(normalizeTimezone(dto.getTimezone()));
        if (dto.getEnabled() != null) {
            schedule.setEnabled(dto.getEnabled());
        }
        schedule.setUpdatedBy(operatorId);
        schedule.setUpdateTime(new Date());
        dutyScheduleMapper.update(schedule);
        replaceSlots(id, dto.getSlots());
        return detail(id);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysDutySchedule schedule = requireSchedule(id);
        schedule.setDeleted(1);
        schedule.setUpdatedBy(operatorId);
        schedule.setUpdateTime(new Date());
        dutyScheduleMapper.update(schedule);
        softDeleteSlots(id);
    }

    private void replaceSlots(Long scheduleId, List<AdminDutyScheduleDTO.SlotDTO> slots) {
        softDeleteSlots(scheduleId);
        if (slots == null || slots.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (AdminDutyScheduleDTO.SlotDTO slotDto : slots) {
            requireAssignee(slotDto.getAssigneeId());
            SysDutyScheduleSlot slot = SysDutyScheduleSlot.builder()
                    .scheduleId(scheduleId)
                    .weekday(slotDto.getWeekday())
                    .startTime(parseTime(slotDto.getStartTime()))
                    .endTime(parseTime(slotDto.getEndTime()))
                    .assigneeId(slotDto.getAssigneeId())
                    .sortOrder(slotDto.getSortOrder() == null ? 0 : slotDto.getSortOrder())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            dutyScheduleSlotMapper.insert(slot);
        }
    }

    private void softDeleteSlots(Long scheduleId) {
        List<SysDutyScheduleSlot> existing = dutyScheduleSlotMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysDutyScheduleSlot::getScheduleId).eq(scheduleId)
                        .and(SysDutyScheduleSlot::getDeleted).eq(0));
        Date now = new Date();
        for (SysDutyScheduleSlot slot : existing) {
            slot.setDeleted(1);
            slot.setUpdateTime(now);
            dutyScheduleSlotMapper.update(slot);
        }
    }

    private SysDutySchedule requireSchedule(Long id) {
        SysDutySchedule schedule = dutyScheduleMapper.selectOneById(id);
        if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())) {
            throw new CustomException(404, "duty schedule not found");
        }
        return schedule;
    }

    private void requireAssignee(Long assigneeId) {
        if (assigneeId == null) {
            throw new CustomException(400, "assignee required");
        }
        SysUser user = sysUserMapper.selectOneById(assigneeId);
        if (user == null) {
            throw new CustomException(400, "assignee not found");
        }
    }

    private AdminDutyScheduleVO toVO(SysDutySchedule schedule, boolean withSlots) {
        List<AdminDutyScheduleVO.SlotVO> slotVos = withSlots ? loadSlots(schedule.getId()) : List.of();
        return AdminDutyScheduleVO.builder()
                .id(schedule.getId())
                .name(schedule.getName())
                .description(schedule.getDescription())
                .timezone(schedule.getTimezone())
                .enabled(schedule.getEnabled())
                .slots(slotVos)
                .createTime(schedule.getCreateTime())
                .updateTime(schedule.getUpdateTime())
                .build();
    }

    private List<AdminDutyScheduleVO.SlotVO> loadSlots(Long scheduleId) {
        List<SysDutyScheduleSlot> slots = dutyScheduleSlotMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysDutyScheduleSlot::getScheduleId).eq(scheduleId)
                        .and(SysDutyScheduleSlot::getDeleted).eq(0)
                        .orderBy(SysDutyScheduleSlot::getWeekday, true)
                        .orderBy(SysDutyScheduleSlot::getSortOrder, true)
                        .orderBy(SysDutyScheduleSlot::getId, true));
        List<AdminDutyScheduleVO.SlotVO> result = new ArrayList<>();
        for (SysDutyScheduleSlot slot : slots) {
            result.add(AdminDutyScheduleVO.SlotVO.builder()
                    .id(slot.getId())
                    .weekday(slot.getWeekday())
                    .startTime(formatTime(slot.getStartTime()))
                    .endTime(formatTime(slot.getEndTime()))
                    .assigneeId(slot.getAssigneeId())
                    .assigneeName(resolveAssigneeName(slot.getAssigneeId()))
                    .sortOrder(slot.getSortOrder())
                    .build());
        }
        result.sort(Comparator.comparing(AdminDutyScheduleVO.SlotVO::getWeekday)
                .thenComparing(s -> s.getSortOrder() == null ? 0 : s.getSortOrder()));
        return result;
    }

    private String resolveAssigneeName(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectOneById(assigneeId);
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private static Time parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            throw new CustomException(400, "time required");
        }
        try {
            LocalTime localTime = LocalTime.parse(value.trim(), TIME_FORMAT);
            return Time.valueOf(localTime);
        } catch (DateTimeParseException e) {
            throw new CustomException(400, "invalid time format");
        }
    }

    private static String formatTime(Time time) {
        if (time == null) {
            return null;
        }
        return time.toLocalTime().format(TIME_FORMAT);
    }

    private static String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new CustomException(400, "name required");
        }
        return name.trim();
    }

    private static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return "Asia/Shanghai";
        }
        return timezone.trim();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
