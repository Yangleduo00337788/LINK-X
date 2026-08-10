package com.linkx.server.service.admin.approval;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.admin.dto.AdminApprovalActionDTO;
import com.linkx.server.controller.admin.dto.AdminApprovalStartDTO;
import com.linkx.server.controller.admin.dto.AdminReviewResolveDTO;
import com.linkx.server.controller.admin.vo.AdminApprovalInstanceVO;
import com.linkx.server.controller.admin.vo.AdminApprovalTimelineItemVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.SysUserRole;
import com.linkx.server.entity.admin.SysApprovalFlow;
import com.linkx.server.entity.admin.SysApprovalInstance;
import com.linkx.server.entity.admin.SysApprovalRecord;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.SysUserRoleMapper;
import com.linkx.server.mapper.admin.SysApprovalFlowMapper;
import com.linkx.server.mapper.admin.SysApprovalInstanceMapper;
import com.linkx.server.mapper.admin.SysApprovalRecordMapper;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.service.EmailService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.ApprovalTempGrantService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApprovalFlowEngine {

    private final SysApprovalFlowMapper flowMapper;
    private final SysApprovalInstanceMapper instanceMapper;
    private final SysApprovalRecordMapper recordMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysReviewTaskMapper reviewTaskMapper;
    private final AdminEventPublisher adminEventPublisher;
    private final ApprovalTempGrantService approvalTempGrantService;
    private final MessageNotificationService notificationService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final AdminReviewService adminReviewService;

    private static final String OFFICIAL_SENDER = "LinkX\u7BA1\u7406\u7AEF";
    private static final String APPROVAL_NOTICE_TYPE = "approval_pending";

    public ApprovalFlowEngine(
            SysApprovalFlowMapper flowMapper,
            SysApprovalInstanceMapper instanceMapper,
            SysApprovalRecordMapper recordMapper,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysReviewTaskMapper reviewTaskMapper,
            AdminEventPublisher adminEventPublisher,
            ApprovalTempGrantService approvalTempGrantService,
            MessageNotificationService notificationService,
            EmailService emailService,
            ObjectMapper objectMapper,
            @Lazy AdminReviewService adminReviewService) {
        this.flowMapper = flowMapper;
        this.instanceMapper = instanceMapper;
        this.recordMapper = recordMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.adminEventPublisher = adminEventPublisher;
        this.approvalTempGrantService = approvalTempGrantService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.adminReviewService = adminReviewService;
    }

    @Transactional
    public AdminApprovalInstanceVO start(AdminApprovalStartDTO dto, Long applicantId) {
        SysApprovalFlow flow = requireFlow(parseLong(dto.getFlowId()));
        if (!Boolean.TRUE.equals(flow.getEnabled())) {
            throw new CustomException(400, "approval flow disabled");
        }
        List<ApprovalFlowStepDef> steps = parseSteps(flow.getStepsJson());
        if (steps.isEmpty()) {
            throw new CustomException(400, "approval flow has no steps");
        }
        String bizType = dto.getBizType().trim().toLowerCase(Locale.ROOT);
        String bizId = dto.getBizId().trim();
        ensureNoPendingInstance(bizType, bizId);

        SysUser applicant = applicantId != null ? sysUserMapper.selectOneById(applicantId) : null;
        Date now = new Date();
        SysApprovalInstance instance = SysApprovalInstance.builder()
                .flowId(flow.getId())
                .flowName(flow.getName())
                .bizType(bizType)
                .bizId(bizId)
                .title(dto.getTitle().trim())
                .status(SysApprovalInstance.STATUS_PENDING)
                .currentStep(0)
                .applicantId(applicantId)
                .applicantName(displayName(applicant))
                .createTime(now)
                .updateTime(now)
                .build();
        instanceMapper.insert(instance);
        linkBizInstance(bizType, bizId, instance.getId());
        activateStep(instance, steps, 0);
        return toInstanceVo(instance, true);
    }

    @Transactional
    public void tryAutoStartForReview(SysReviewTask task, Long operatorId) {
        if (task == null || task.getId() == null) {
            return;
        }
        if (!"high".equalsIgnoreCase(task.getRiskLevel()) && !"critical".equalsIgnoreCase(task.getRiskLevel())) {
            return;
        }
        if (task.getApprovalInstanceId() != null) {
            return;
        }
        SysApprovalFlow flow = findAutoStartFlow("review");
        if (flow == null) {
            return;
        }
        try {
            AdminApprovalStartDTO dto = new AdminApprovalStartDTO();
            dto.setFlowId(String.valueOf(flow.getId()));
            dto.setBizType("review");
            dto.setBizId(String.valueOf(task.getId()));
            dto.setTitle(StringUtils.hasText(task.getTitle()) ? task.getTitle() : "审核任务 #" + task.getId());
            start(dto, operatorId);
        } catch (Exception ex) {
            log.debug("Auto approval skipped for review {}: {}", task.getId(), ex.getMessage());
        }
    }

    @Transactional
    public void approveRecord(Long recordId, AdminApprovalActionDTO dto, Long operatorId) {
        processRecord(recordId, operatorId, true, dto);
    }

    @Transactional
    public void rejectRecord(Long recordId, AdminApprovalActionDTO dto, Long operatorId) {
        processRecord(recordId, operatorId, false, dto);
    }

    public AdminApprovalInstanceVO instanceDetail(Long instanceId) {
        SysApprovalInstance instance = requireInstance(instanceId);
        return toInstanceVo(instance, true);
    }

    public List<ApprovalFlowStepDef> parseSteps(String stepsJson) {
        if (!StringUtils.hasText(stepsJson)) {
            return List.of();
        }
        try {
            List<ApprovalFlowStepDef> steps = objectMapper.readValue(
                    stepsJson, new TypeReference<List<ApprovalFlowStepDef>>() {});
            return steps == null ? List.of() : steps;
        } catch (Exception ex) {
            throw new CustomException(400, "invalid steps_json");
        }
    }

    private void processRecord(Long recordId, Long operatorId, boolean approve, AdminApprovalActionDTO dto) {
        SysApprovalRecord record = requireRecord(recordId);
        if (!Objects.equals(record.getAssigneeId(), operatorId)) {
            throw new CustomException(403, "not assignee");
        }
        if (!SysApprovalRecord.STATUS_PENDING.equals(record.getStatus())) {
            throw new CustomException(400, "record already processed");
        }
        SysApprovalInstance instance = requireInstance(record.getInstanceId());
        if (!SysApprovalInstance.STATUS_PENDING.equals(instance.getStatus())) {
            throw new CustomException(400, "instance not pending");
        }
        if (!Objects.equals(instance.getCurrentStep(), record.getStepIndex())) {
            throw new CustomException(400, "step already advanced");
        }
        if (SysApprovalRecord.NODE_CC.equals(record.getNodeType())) {
            throw new CustomException(400, "cc record cannot be approved");
        }

        Date now = new Date();
        record.setStatus(approve ? SysApprovalRecord.STATUS_APPROVED : SysApprovalRecord.STATUS_REJECTED);
        record.setComment(dto != null ? dto.getComment() : null);
        record.setActionTime(now);
        recordMapper.update(record);
        approvalTempGrantService.revokeForRecord(record.getId());

        if (!approve) {
            finishInstance(instance, SysApprovalInstance.STATUS_REJECTED);
            syncBizOnFinish(instance, false, operatorId);
            return;
        }

        if (!isStepComplete(instance.getId(), record.getStepIndex(), record.getNodeType())) {
            return;
        }
        advance(instance, operatorId);
    }

    private void advance(SysApprovalInstance instance, Long lastOperatorId) {
        SysApprovalFlow flow = requireFlow(instance.getFlowId());
        List<ApprovalFlowStepDef> steps = parseSteps(flow.getStepsJson());
        int next = instance.getCurrentStep() + 1;
        if (next >= steps.size()) {
            finishInstance(instance, SysApprovalInstance.STATUS_APPROVED);
            syncBizOnFinish(instance, true, lastOperatorId);
            return;
        }
        instance.setCurrentStep(next);
        instance.setUpdateTime(new Date());
        instanceMapper.update(instance);
        activateStep(instance, steps, next);
    }

    private void activateStep(SysApprovalInstance instance, List<ApprovalFlowStepDef> steps, int stepIndex) {
        ApprovalFlowStepDef step = steps.get(stepIndex);
        String nodeType = normalizeNodeType(step.getNodeType());
        String stepName = StringUtils.hasText(step.getName()) ? step.getName().trim() : ("步骤 " + (stepIndex + 1));
        List<Long> assignees = resolveAssignees(step);
        if (assignees.isEmpty()) {
            throw new CustomException(400, "step has no assignees: " + stepName);
        }
        Date now = new Date();
        for (Long assigneeId : assignees) {
            SysUser user = sysUserMapper.selectOneById(assigneeId);
            SysApprovalRecord record = SysApprovalRecord.builder()
                    .instanceId(instance.getId())
                    .stepIndex(stepIndex)
                    .stepName(stepName)
                    .nodeType(nodeType)
                    .assigneeId(assigneeId)
                    .assigneeName(displayName(user))
                    .status(SysApprovalRecord.NODE_CC.equals(nodeType)
                            ? SysApprovalRecord.STATUS_READ
                            : SysApprovalRecord.STATUS_PENDING)
                    .actionTime(SysApprovalRecord.NODE_CC.equals(nodeType) ? now : null)
                    .createTime(now)
                    .build();
            recordMapper.insert(record);
            if (!SysApprovalRecord.NODE_CC.equals(nodeType)) {
                approvalTempGrantService.grantForRecord(record.getId(), assigneeId);
                notifyAssignee(instance, record, stepName, user);
            }
            if (SysApprovalRecord.NODE_CC.equals(nodeType)) {
                adminEventPublisher.publishToUsers(
                        "approval_cc",
                        instance.getId(),
                        List.of(assigneeId),
                        "{\"recordId\":" + record.getId() + ",\"title\":\"" + escape(instance.getTitle()) + "\"}");
            } else {
                adminEventPublisher.publishToUsers(
                        "approval_pending",
                        record.getId(),
                        List.of(assigneeId),
                        "{\"instanceId\":" + instance.getId()
                                + ",\"title\":\"" + escape(instance.getTitle())
                                + "\",\"stepName\":\"" + escape(stepName) + "\"}");
            }
        }
        if (SysApprovalRecord.NODE_CC.equals(nodeType)) {
            advance(instance, null);
        }
    }

    private boolean isStepComplete(Long instanceId, int stepIndex, String nodeType) {
        List<SysApprovalRecord> records = recordMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysApprovalRecord::getInstanceId).eq(instanceId)
                        .and(SysApprovalRecord::getStepIndex).eq(stepIndex));
        if (SysApprovalRecord.NODE_APPROVE.equals(nodeType)) {
            return records.stream().anyMatch(r -> SysApprovalRecord.STATUS_APPROVED.equals(r.getStatus()));
        }
        if (SysApprovalRecord.NODE_COUNTERSIGN.equals(nodeType)) {
            return records.stream()
                    .allMatch(r -> SysApprovalRecord.STATUS_APPROVED.equals(r.getStatus()));
        }
        return true;
    }

    private void finishInstance(SysApprovalInstance instance, String status) {
        Date now = new Date();
        instance.setStatus(status);
        instance.setFinishedAt(now);
        instance.setUpdateTime(now);
        instanceMapper.update(instance);
        approvalTempGrantService.revokeForInstance(instance.getId());
        Set<Long> targets = new LinkedHashSet<>();
        if (instance.getApplicantId() != null) {
            targets.add(instance.getApplicantId());
        }
        recordMapper.selectListByQuery(
                        QueryWrapper.create().where(SysApprovalRecord::getInstanceId).eq(instance.getId()))
                .forEach(r -> {
                    if (r.getAssigneeId() != null) {
                        targets.add(r.getAssigneeId());
                    }
                });
        adminEventPublisher.publishToUsers(
                "approval_finished",
                instance.getId(),
                targets,
                "{\"status\":\"" + status + "\"}");
    }

    private void notifyAssignee(
            SysApprovalInstance instance,
            SysApprovalRecord record,
            String stepName,
            SysUser assignee) {
        if (assignee == null || assignee.getId() == null) {
            return;
        }
        Long assigneeId = assignee.getId();
        String body = "您有新的审批待办【" + stepName + "】：" + instance.getTitle()
                + "\n请登录管理端「审批待办」处理。";
        try {
            notificationService.create(
                    assigneeId,
                    instance.getApplicantId(),
                    OFFICIAL_SENDER,
                    null,
                    APPROVAL_NOTICE_TYPE,
                    record.getId(),
                    body);
        } catch (Exception ex) {
            log.warn("审批站内信发送失败 userId={}, recordId={}: {}", assigneeId, record.getId(), ex.getMessage());
        }
        if (StringUtils.hasText(assignee.getEmail())) {
            try {
                emailService.sendApprovalPendingNotification(
                        assignee.getEmail().trim(),
                        displayName(assignee),
                        instance.getTitle(),
                        stepName);
            } catch (Exception ex) {
                log.warn("审批邮件发送失败 userId={}, recordId={}: {}", assigneeId, record.getId(), ex.getMessage());
            }
        }
        adminEventPublisher.publishToUsers(
                "permissions_refresh",
                assigneeId,
                List.of(assigneeId),
                "{}");
    }

    private void syncBizOnFinish(SysApprovalInstance instance, boolean approved, Long operatorId) {
        if (!"review".equals(instance.getBizType())) {
            return;
        }
        Long reviewId = parseLong(instance.getBizId());
        if (reviewId == null) {
            return;
        }
        SysReviewTask task = reviewTaskMapper.selectOneById(reviewId);
        if (task == null || !SysReviewTask.STATUS_PENDING.equals(task.getStatus())) {
            return;
        }
        AdminReviewResolveDTO dto = new AdminReviewResolveDTO();
        dto.setResolution(approved ? "审批流程通过" : "审批流程驳回");
        Long op = operatorId != null ? operatorId : instance.getApplicantId();
        try {
            if (approved) {
                adminReviewService.approve(reviewId, dto, op);
            } else {
                adminReviewService.reject(reviewId, dto, op);
            }
        } catch (Exception ex) {
            log.warn("Sync review {} after approval failed: {}", reviewId, ex.getMessage());
        }
    }

    private void linkBizInstance(String bizType, String bizId, Long instanceId) {
        if (!"review".equals(bizType)) {
            return;
        }
        Long reviewId = parseLong(bizId);
        if (reviewId == null) {
            return;
        }
        SysReviewTask task = reviewTaskMapper.selectOneById(reviewId);
        if (task == null) {
            return;
        }
        task.setApprovalInstanceId(instanceId);
        task.setUpdateTime(new Date());
        reviewTaskMapper.update(task);
    }

    private void ensureNoPendingInstance(String bizType, String bizId) {
        long pending = instanceMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysApprovalInstance::getBizType).eq(bizType)
                        .and(SysApprovalInstance::getBizId).eq(bizId)
                        .and(SysApprovalInstance::getStatus).eq(SysApprovalInstance.STATUS_PENDING));
        if (pending > 0) {
            throw new CustomException(400, "pending approval already exists");
        }
    }

    private SysApprovalFlow findAutoStartFlow(String bizType) {
        List<SysApprovalFlow> flows = flowMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysApprovalFlow::getDeleted).eq(0)
                        .and(SysApprovalFlow::getEnabled).eq(true)
                        .and(SysApprovalFlow::getAutoStart).eq(true)
                        .and(SysApprovalFlow::getBizType).eq(bizType)
                        .orderBy(SysApprovalFlow::getPriority, false)
                        .orderBy(SysApprovalFlow::getUpdateTime, false)
                        .limit(1));
        return flows.isEmpty() ? null : flows.get(0);
    }

    private List<Long> resolveAssignees(ApprovalFlowStepDef step) {
        Set<Long> ids = new LinkedHashSet<>();
        if (step.getAssigneeIds() != null) {
            for (String raw : step.getAssigneeIds()) {
                Long id = parseLong(raw);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        String assigneeType = step.getAssigneeType() == null
                ? "user"
                : step.getAssigneeType().trim().toLowerCase(Locale.ROOT);
        Long ref = parseLong(step.getAssigneeId());
        if ("role".equals(assigneeType)) {
            if (ref == null) {
                return List.of();
            }
            List<SysUserRole> bindings = sysUserRoleMapper.selectListByQuery(
                    QueryWrapper.create().where(SysUserRole::getRoleId).eq(ref));
            for (SysUserRole binding : bindings) {
                if (binding.getUserId() != null) {
                    ids.add(binding.getUserId());
                }
            }
        } else if (ref != null) {
            ids.add(ref);
        }
        return new ArrayList<>(ids);
    }

    private AdminApprovalInstanceVO toInstanceVo(SysApprovalInstance instance, boolean withTimeline) {
        AdminApprovalInstanceVO.AdminApprovalInstanceVOBuilder builder = AdminApprovalInstanceVO.builder()
                .id(String.valueOf(instance.getId()))
                .flowId(String.valueOf(instance.getFlowId()))
                .flowName(instance.getFlowName())
                .bizType(instance.getBizType())
                .bizId(instance.getBizId())
                .title(instance.getTitle())
                .status(instance.getStatus())
                .currentStep(instance.getCurrentStep())
                .applicantId(instance.getApplicantId() != null ? String.valueOf(instance.getApplicantId()) : null)
                .applicantName(instance.getApplicantName())
                .finishedAt(instance.getFinishedAt())
                .createTime(instance.getCreateTime());
        if (withTimeline) {
            builder.timeline(loadTimeline(instance.getId()));
        }
        return builder.build();
    }

    private List<AdminApprovalTimelineItemVO> loadTimeline(Long instanceId) {
        List<SysApprovalRecord> records = new ArrayList<>(recordMapper.selectListByQuery(
                QueryWrapper.create().where(SysApprovalRecord::getInstanceId).eq(instanceId)));
        records.sort(Comparator
                .comparing(SysApprovalRecord::getStepIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(this::timelineSortTime)
                .thenComparing(SysApprovalRecord::getId, Comparator.nullsLast(Long::compareTo)));
        return records.stream()
                .map(r -> AdminApprovalTimelineItemVO.builder()
                        .id(String.valueOf(r.getId()))
                        .stepIndex(r.getStepIndex())
                        .stepName(r.getStepName())
                        .nodeType(r.getNodeType())
                        .assigneeId(String.valueOf(r.getAssigneeId()))
                        .assigneeName(r.getAssigneeName())
                        .status(r.getStatus())
                        .comment(r.getComment())
                        .actionTime(r.getActionTime())
                        .build())
                .collect(Collectors.toList());
    }

    private Date timelineSortTime(SysApprovalRecord record) {
        if (record.getActionTime() != null) {
            return record.getActionTime();
        }
        return record.getCreateTime() != null ? record.getCreateTime() : new Date(0);
    }

    private SysApprovalFlow requireFlow(Long id) {
        SysApprovalFlow flow = flowMapper.selectOneById(id);
        if (flow == null || flow.getDeleted() != null && flow.getDeleted() == 1) {
            throw new CustomException(404, "approval flow not found");
        }
        return flow;
    }

    private SysApprovalInstance requireInstance(Long id) {
        SysApprovalInstance instance = instanceMapper.selectOneById(id);
        if (instance == null) {
            throw new CustomException(404, "approval instance not found");
        }
        return instance;
    }

    private SysApprovalRecord requireRecord(Long id) {
        SysApprovalRecord record = recordMapper.selectOneById(id);
        if (record == null) {
            throw new CustomException(404, "approval record not found");
        }
        return record;
    }

    private static String normalizeNodeType(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return SysApprovalRecord.NODE_APPROVE;
        }
        String n = nodeType.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "countersign", "counter_sign", "会签" -> SysApprovalRecord.NODE_COUNTERSIGN;
            case "cc", "copy", "抄送" -> SysApprovalRecord.NODE_CC;
            default -> SysApprovalRecord.NODE_APPROVE;
        };
    }

    private static String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private static Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'");
    }
}
