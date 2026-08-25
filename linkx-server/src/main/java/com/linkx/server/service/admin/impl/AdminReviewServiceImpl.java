package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.AdminKeywordQuery;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminReviewBatchDTO;
import com.linkx.server.controller.admin.dto.AdminReviewQueryDTO;
import com.linkx.server.controller.admin.dto.AdminReviewResolveDTO;
import com.linkx.server.controller.admin.dto.AdminUserActionDTO;
import com.linkx.server.controller.admin.vo.AdminReviewBatchResultVO;
import com.linkx.server.controller.admin.vo.AdminReviewVO;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.vo.MessageVO;
import com.linkx.server.entity.Favorite;
import com.linkx.server.entity.Feedback;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.entity.admin.SysApprovalInstance;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.mapper.FeedbackMapper;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysReviewTaskMapper;
import com.linkx.server.mapper.admin.SysApprovalInstanceMapper;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.service.ChatService;
import com.linkx.server.service.FavoriteService;
import com.linkx.server.service.GroupAnnouncementService;
import com.linkx.server.service.GroupAssetService;
import com.linkx.server.service.GroupService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.MomentsService;
import com.linkx.server.service.ShortVideoService;
import com.linkx.server.service.RbacService;
import com.linkx.server.service.admin.AdminAudienceService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.service.admin.ReviewRiskScoringService;
import com.linkx.server.service.admin.approval.ApprovalFlowEngine;
import com.linkx.server.service.admin.AdminUserService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminReviewServiceImpl implements AdminReviewService {

    private static final String OFFICIAL_SENDER = "LinkX\u5B98\u65B9";
    private static final Pattern REPORT_PREFIX = Pattern.compile("^\\[举报([^\\]]*)\\]");
    private static final Pattern GROUP_ID_LINE = Pattern.compile("(?m)^群ID:\\s*(.+)$");
    private static final Pattern USER_ID_LINE = Pattern.compile("(?m)^用户ID:\\s*(.+)$");
    private static final Pattern POST_ID_LINE = Pattern.compile("(?m)^作品ID:\\s*(.+)$");
    private static final Pattern COMMENT_ID_LINE = Pattern.compile("(?m)^评论ID:\\s*(.+)$");
    private static final Pattern AUTHOR_ID_LINE = Pattern.compile("(?m)^作者ID:\\s*(.+)$");
    private static final Pattern EVIDENCE_KEY_LINE = Pattern.compile(
            "(?m)^\\d+\\.\\s*([\\w./-]+\\.(?:png|jpe?g|gif|webp|bmp))$",
            Pattern.CASE_INSENSITIVE
    );

    private final SysReviewTaskMapper reviewTaskMapper;
    private final FeedbackMapper feedbackMapper;
    private final SysUserMapper sysUserMapper;
    private final MessageNotificationService notificationService;
    private final ImMessagePushService imPushService;
    private final AdminEventPublisher adminEventPublisher;
    private final AdminAudienceService adminAudienceService;
    private final MediaUrlService mediaUrlService;
    private final AdminUserService adminUserService;
    private final RbacService rbacService;
    private final ChatService chatService;
    private final MomentsService momentsService;
    private final ShortVideoService shortVideoService;
    private final GroupAnnouncementService groupAnnouncementService;
    private final GroupAssetService groupAssetService;
    private final FavoriteService favoriteService;
    private final GroupService groupService;
    private final ImConversationMapper conversationMapper;
    private final GroupAssetMapper groupAssetMapper;
    private final FavoriteMapper favoriteMapper;
    private final LinkxProperties linkxProperties;
    private final ReviewRiskScoringService reviewRiskScoringService;
    private final ApprovalFlowEngine approvalFlowEngine;
    private final SysApprovalInstanceMapper approvalInstanceMapper;

    public AdminReviewServiceImpl(
            SysReviewTaskMapper reviewTaskMapper,
            FeedbackMapper feedbackMapper,
            SysUserMapper sysUserMapper,
            MessageNotificationService notificationService,
            ImMessagePushService imPushService,
            AdminEventPublisher adminEventPublisher,
            AdminAudienceService adminAudienceService,
            MediaUrlService mediaUrlService,
            AdminUserService adminUserService,
            RbacService rbacService,
            @Lazy ChatService chatService,
            @Lazy MomentsService momentsService,
            @Lazy ShortVideoService shortVideoService,
            @Lazy GroupAnnouncementService groupAnnouncementService,
            @Lazy GroupAssetService groupAssetService,
            @Lazy FavoriteService favoriteService,
            @Lazy GroupService groupService,
            ImConversationMapper conversationMapper,
            GroupAssetMapper groupAssetMapper,
            FavoriteMapper favoriteMapper,
            LinkxProperties linkxProperties,
            ReviewRiskScoringService reviewRiskScoringService,
            @Lazy ApprovalFlowEngine approvalFlowEngine,
            SysApprovalInstanceMapper approvalInstanceMapper) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.feedbackMapper = feedbackMapper;
        this.sysUserMapper = sysUserMapper;
        this.notificationService = notificationService;
        this.imPushService = imPushService;
        this.adminEventPublisher = adminEventPublisher;
        this.adminAudienceService = adminAudienceService;
        this.mediaUrlService = mediaUrlService;
        this.adminUserService = adminUserService;
        this.rbacService = rbacService;
        this.chatService = chatService;
        this.momentsService = momentsService;
        this.shortVideoService = shortVideoService;
        this.groupAnnouncementService = groupAnnouncementService;
        this.groupAssetService = groupAssetService;
        this.favoriteService = favoriteService;
        this.groupService = groupService;
        this.conversationMapper = conversationMapper;
        this.groupAssetMapper = groupAssetMapper;
        this.favoriteMapper = favoriteMapper;
        this.linkxProperties = linkxProperties;
        this.reviewRiskScoringService = reviewRiskScoringService;
        this.approvalFlowEngine = approvalFlowEngine;
        this.approvalInstanceMapper = approvalInstanceMapper;
    }

    @Override
    public PageResultVO<AdminReviewVO> list(AdminReviewQueryDTO query) {
        ensureReportTasks();
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildListQuery(query);
        // count 不可带 ORDER BY：H2 会报 create_time 须出现在 GROUP BY
        long total = reviewTaskMapper.selectCountByQuery(qw);
        qw.orderBy(SysReviewTask::getCreateTime, false);
        qw.limit((page - 1L) * size, size);
        List<AdminReviewVO> items = reviewTaskMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    private QueryWrapper buildListQuery(AdminReviewQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create();
        String kw = AdminKeywordQuery.forLike(query.getKeyword());
        if (kw != null) {
            qw.and((QueryWrapper w) -> {
                w.where(SysReviewTask::getTitle).like(kw)
                        .or(SysReviewTask::getContentSnapshot).like(kw)
                        .or(SysReviewTask::getReporterUsername).like(kw)
                        .or(SysReviewTask::getTargetId).like(kw);
            });
        }
        Date keywordFloor = AdminKeywordQuery.createTimeFloorOrNull(query.getStartTime(), query.getKeyword());
        if (keywordFloor != null) {
            qw.and(SysReviewTask::getCreateTime).ge(keywordFloor);
        }
        if (StringUtils.hasText(query.getReviewStatus())) {
            qw.and(SysReviewTask::getStatus).eq(query.getReviewStatus().trim());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            qw.and(SysReviewTask::getSourceType).eq(query.getSourceType().trim());
        }
        if (StringUtils.hasText(query.getTargetType())) {
            qw.and(SysReviewTask::getTargetType).eq(query.getTargetType().trim());
        }
        if (StringUtils.hasText(query.getRiskLevel())) {
            qw.and(SysReviewTask::getRiskLevel).eq(query.getRiskLevel().trim());
        }
        if (query.getStartTime() != null) {
            qw.and(SysReviewTask::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysReviewTask::getCreateTime).le(new Date(query.getEndTime()));
        }
        if (Boolean.TRUE.equals(query.getOverdueOnly())) {
            qw.and(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING);
            qw.and(SysReviewTask::getCreateTime).le(ReviewEscalationServiceImpl.slaCutoff(linkxProperties));
        }
        if (Boolean.TRUE.equals(query.getEscalatedOnly())) {
            qw.and(SysReviewTask::getEscalationCount).gt(0);
        }
        return qw;
    }

    @Override
    public List<AdminReviewVO> listForExport(AdminReviewQueryDTO query) {
        ensureReportTasks();
        QueryWrapper qw = buildListQuery(query);
        qw.orderBy(SysReviewTask::getCreateTime, false);
        qw.limit(0, AdminConstants.EXPORT_MAX_SIZE);
        return reviewTaskMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public AdminReviewVO detail(Long id) {
        SysReviewTask task = requireTask(id);
        AdminReviewVO vo = toVO(task);
        vo.setRiskContext(reviewRiskScoringService.buildContext(task));
        return vo;
    }

    @Override
    @Transactional
    public void approve(Long id, AdminReviewResolveDTO dto, Long operatorId) {
        resolve(id, SysReviewTask.STATUS_APPROVED, dto, operatorId);
    }

    @Override
    @Transactional
    public void reject(Long id, AdminReviewResolveDTO dto, Long operatorId) {
        resolve(id, SysReviewTask.STATUS_REJECTED, dto, operatorId);
    }

    @Override
    @Transactional
    public void deleteContent(Long id, AdminReviewResolveDTO dto, Long operatorId) {
        SysReviewTask task = requireTask(id);
        if (!SysReviewTask.STATUS_PENDING.equals(task.getStatus())) {
            throw new CustomException(400, "仅待处理任务可下架内容");
        }
        String appliedContent = applyContentAction(task);
        Date now = new Date();
        task.setStatus(SysReviewTask.STATUS_APPROVED);
        task.setResolution(buildResolution(
                dto != null ? dto.getResolution() : null, appliedContent, "none", "none"));
        task.setResolvedBy(operatorId);
        task.setResolvedAt(now);
        task.setUpdateTime(now);
        reviewTaskMapper.update(task);
        afterTaskResolved(task, SysReviewTask.STATUS_APPROVED, operatorId);
    }

    @Override
    @Transactional
    public AdminReviewBatchResultVO batch(AdminReviewBatchDTO dto, Long operatorId) {
        String action = dto.getAction() == null ? "" : dto.getAction().trim().toLowerCase();
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new CustomException(400, "action must be approve or reject");
        }
        String status = "approve".equals(action)
                ? SysReviewTask.STATUS_APPROVED
                : SysReviewTask.STATUS_REJECTED;
        AdminReviewResolveDTO resolveDto = new AdminReviewResolveDTO();
        resolveDto.setResolution(dto.getResolution());
        if ("approve".equals(action)) {
            resolveDto.setUserAction(dto.getUserAction());
            resolveDto.setContentAction(dto.getContentAction());
            resolveDto.setGroupAction(dto.getGroupAction());
        }

        int success = 0;
        List<AdminReviewBatchResultVO.FailureItem> failures = new ArrayList<>();
        for (Long id : dto.getIds()) {
            if (id == null) {
                continue;
            }
            try {
                resolve(id, status, resolveDto, operatorId);
                success++;
            } catch (CustomException ex) {
                failures.add(AdminReviewBatchResultVO.FailureItem.builder()
                        .id(id)
                        .reason(ex.getMessage())
                        .build());
            } catch (Exception ex) {
                failures.add(AdminReviewBatchResultVO.FailureItem.builder()
                        .id(id)
                        .reason(ex.getMessage() != null ? ex.getMessage() : "unknown error")
                        .build());
            }
        }
        return AdminReviewBatchResultVO.builder()
                .successCount(success)
                .failCount(failures.size())
                .failures(failures)
                .build();
    }

    @Override
    @Transactional
    public void createFromReportFeedback(Feedback feedback) {
        if (feedback == null || feedback.getId() == null || !isReportContent(feedback.getContent())) {
            return;
        }
        SysReviewTask existing = reviewTaskMapper.selectOneByQuery(
                QueryWrapper.create().where(SysReviewTask::getFeedbackId).eq(feedback.getId()));
        if (existing != null) {
            return;
        }
        SysReviewTask task = buildFromFeedback(feedback);
        reviewTaskMapper.insert(task);
        afterReviewCreated(task);
    }

    @Override
    @Transactional
    public void createFromShortVideoReport(Long reporterId,
                                           String reporterUsername,
                                           Long postId,
                                           Long authorId,
                                           String authorNickname,
                                           String reason,
                                           String detail,
                                           List<String> imageKeys) {
        if (reporterId == null || postId == null) {
            return;
        }
        String postIdStr = String.valueOf(postId);
        SysReviewTask existing = reviewTaskMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getSourceType).eq(SysReviewTask.SOURCE_REPORT)
                        .and(SysReviewTask::getTargetType).eq(SysReviewTask.TARGET_SHORT_VIDEO)
                        .and(SysReviewTask::getTargetId).eq(postIdStr)
                        .and(SysReviewTask::getReporterUserId).eq(reporterId)
                        .and(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING));
        if (existing != null) {
            return;
        }
        String reasonLabel = resolveShortVideoReportReasonLabel(reason);
        String detailText = StringUtils.hasText(detail) ? detail.trim() : "-";
        StringBuilder sb = new StringBuilder();
        sb.append("[举报短视频]\n");
        sb.append("作品ID: ").append(postId).append('\n');
        sb.append("作者ID: ").append(authorId != null ? authorId : "-").append('\n');
        sb.append("作者: ").append(StringUtils.hasText(authorNickname) ? authorNickname.trim() : "-").append('\n');
        sb.append("原因: ").append(reasonLabel).append('\n');
        sb.append("说明: ").append(detailText).append('\n');
        if (imageKeys != null && !imageKeys.isEmpty()) {
            sb.append("证据:\n");
            for (int i = 0; i < imageKeys.size(); i++) {
                sb.append(i + 1).append(". ").append(imageKeys.get(i).trim()).append('\n');
            }
        } else {
            sb.append("证据: 无\n");
        }
        String content = sb.toString();
        Date now = new Date();
        Long subjectUserId = authorId != null ? authorId
                : shortVideoService.findPostAuthorId(postId);
        String riskLevel = reviewRiskScoringService.elevateLevel("medium", subjectUserId);
        SysReviewTask task = SysReviewTask.builder()
                .sourceType(SysReviewTask.SOURCE_REPORT)
                .targetType(SysReviewTask.TARGET_SHORT_VIDEO)
                .targetId(postIdStr)
                .reporterUserId(reporterId)
                .reporterUsername(reporterUsername)
                .title("举报短视频")
                .contentSnapshot(content)
                .riskLevel(riskLevel)
                .status(SysReviewTask.STATUS_PENDING)
                .escalationCount(0)
                .createTime(now)
                .updateTime(now)
                .build();
        reviewTaskMapper.insert(task);
        afterReviewCreated(task);
    }

    @Override
    @Transactional
    public void createFromShortVideoCommentReport(Long reporterId,
                                                  String reporterUsername,
                                                  Long postId,
                                                  Long commentId,
                                                  Long authorId,
                                                  String authorNickname,
                                                  String reason,
                                                  String detail,
                                                  List<String> imageKeys) {
        if (reporterId == null || commentId == null) {
            return;
        }
        String commentIdStr = String.valueOf(commentId);
        SysReviewTask existing = reviewTaskMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getSourceType).eq(SysReviewTask.SOURCE_REPORT)
                        .and(SysReviewTask::getTargetType).eq(SysReviewTask.TARGET_SHORT_VIDEO_COMMENT)
                        .and(SysReviewTask::getTargetId).eq(commentIdStr)
                        .and(SysReviewTask::getReporterUserId).eq(reporterId)
                        .and(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING));
        if (existing != null) {
            return;
        }
        String reasonLabel = resolveShortVideoReportReasonLabel(reason);
        String detailText = StringUtils.hasText(detail) ? detail.trim() : "-";
        StringBuilder sb = new StringBuilder();
        sb.append("[举报短视频评论]\n");
        sb.append("作品ID: ").append(postId != null ? postId : "-").append('\n');
        sb.append("评论ID: ").append(commentId).append('\n');
        sb.append("作者ID: ").append(authorId != null ? authorId : "-").append('\n');
        sb.append("作者: ").append(StringUtils.hasText(authorNickname) ? authorNickname.trim() : "-").append('\n');
        sb.append("原因: ").append(reasonLabel).append('\n');
        sb.append("说明: ").append(detailText).append('\n');
        if (imageKeys != null && !imageKeys.isEmpty()) {
            sb.append("证据:\n");
            for (int i = 0; i < imageKeys.size(); i++) {
                sb.append(i + 1).append(". ").append(imageKeys.get(i).trim()).append('\n');
            }
        } else {
            sb.append("证据: 无\n");
        }
        String content = sb.toString();
        Date now = new Date();
        Long subjectUserId = authorId != null ? authorId
                : shortVideoService.findCommentAuthorId(commentId);
        String riskLevel = reviewRiskScoringService.elevateLevel("medium", subjectUserId);
        SysReviewTask task = SysReviewTask.builder()
                .sourceType(SysReviewTask.SOURCE_REPORT)
                .targetType(SysReviewTask.TARGET_SHORT_VIDEO_COMMENT)
                .targetId(commentIdStr)
                .reporterUserId(reporterId)
                .reporterUsername(reporterUsername)
                .title("举报短视频评论")
                .contentSnapshot(content)
                .riskLevel(riskLevel)
                .status(SysReviewTask.STATUS_PENDING)
                .escalationCount(0)
                .createTime(now)
                .updateTime(now)
                .build();
        reviewTaskMapper.insert(task);
        afterReviewCreated(task);
    }

    private static String resolveShortVideoReportReasonLabel(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "其他";
        }
        String code = reason.trim();
        return switch (code) {
            case "spam" -> "垃圾信息";
            case "harassment" -> "骚扰辱骂";
            case "fraud" -> "欺诈诈骗";
            case "porn" -> "色情低俗";
            case "illegal" -> "违法违规";
            case "other" -> "其他";
            default -> code;
        };
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createFromSensitiveHit(Long userId,
                                       String targetType,
                                       String targetId,
                                       Long conversationId,
                                       String content,
                                       String matchedWords,
                                       String failReason) {
        if (!StringUtils.hasText(targetType) || !StringUtils.hasText(targetId)) {
            return;
        }
        String type = targetType.trim();
        String tid = targetId.trim();
        // 拦截发送无消息 ID：用唯一 targetId，避免与同会话其它待审冲突；也避免被外层事务回滚
        if ("blocked".equals(failReason) && SysReviewTask.TARGET_CONVERSATION.equals(type)) {
            tid = tid + ":blocked:" + System.currentTimeMillis();
        }
        SysReviewTask existing = reviewTaskMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getSourceType).eq(SysReviewTask.SOURCE_SENSITIVE)
                        .and(SysReviewTask::getTargetType).eq(type)
                        .and(SysReviewTask::getTargetId).eq(tid)
                        .and(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING)
                        .limit(1)
        );
        if (existing != null) {
            return;
        }

        String reason = StringUtils.hasText(failReason) ? failReason.trim() : "matched";
        String riskLevel = "blocked".equals(reason)
                ? "high"
                : ("alert".equals(reason) ? "medium" : "low");
        String words = matchedWords == null ? "" : matchedWords.trim();
        String username = resolveUsername(userId);

        StringBuilder snapshot = new StringBuilder();
        snapshot.append("敏感词命中 (").append(reason).append(")\n");
        if (userId != null) {
            snapshot.append("用户ID: ").append(userId).append('\n');
            if (StringUtils.hasText(username)) {
                snapshot.append("用户名: ").append(username).append('\n');
            }
        }
        if (conversationId != null) {
            snapshot.append("会话ID: ").append(conversationId).append('\n');
        }
        snapshot.append("目标类型: ").append(type).append('\n');
        snapshot.append("目标ID: ").append(tid).append('\n');
        if (StringUtils.hasText(words)) {
            snapshot.append("敏感词: ").append(words).append('\n');
        }
        if (StringUtils.hasText(content)) {
            snapshot.append("内容: ").append(abbreviate(content.trim(), 800));
        }

        String title = "敏感词命中";
        if (StringUtils.hasText(words)) {
            title = "敏感词命中: " + abbreviate(words, 40);
        }

        Date now = new Date();
        SysReviewTask task = SysReviewTask.builder()
                .sourceType(SysReviewTask.SOURCE_SENSITIVE)
                .targetType(type)
                .targetId(tid)
                .reporterUserId(null)
                .reporterUsername("系统")
                .title(title)
                .contentSnapshot(snapshot.toString())
                .riskLevel(riskLevel)
                .status(SysReviewTask.STATUS_PENDING)
                .escalationCount(0)
                .createTime(now)
                .updateTime(now)
                .build();
        reviewTaskMapper.insert(task);
        afterReviewCreated(task);
    }

    @Override
    @Transactional
    public void ensureReportTasks() {
        List<Feedback> reports = feedbackMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Feedback::getContent).like("[举报%")
                        .and(Feedback::getStatus).eq("pending")
                        .orderBy(Feedback::getCreateTime, false)
                        .limit(200)
        );
        if (reports.isEmpty()) {
            return;
        }
        Set<Long> feedbackIds = reports.stream().map(Feedback::getId).collect(Collectors.toSet());
        List<SysReviewTask> existing = reviewTaskMapper.selectListByQuery(
                QueryWrapper.create().where(SysReviewTask::getFeedbackId).in(feedbackIds));
        Set<Long> linked = existing.stream()
                .map(SysReviewTask::getFeedbackId)
                .collect(Collectors.toSet());
        for (Feedback feedback : reports) {
            if (!linked.contains(feedback.getId())) {
                SysReviewTask task = buildFromFeedback(feedback);
                reviewTaskMapper.insert(task);
                afterReviewCreated(task);
            }
        }
    }

    @Override
    public long countPending() {
        ensureReportTasks();
        return reviewTaskMapper.selectCountByQuery(
                QueryWrapper.create().where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING));
    }

    @Override
    public long countOverdue() {
        ensureReportTasks();
        return reviewTaskMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING)
                        .and(SysReviewTask::getCreateTime).le(ReviewEscalationServiceImpl.slaCutoff(linkxProperties)));
    }

    @Override
    public long countPendingBySource(String sourceType) {
        ensureReportTasks();
        QueryWrapper qw = QueryWrapper.create()
                .where(SysReviewTask::getStatus).eq(SysReviewTask.STATUS_PENDING);
        if (StringUtils.hasText(sourceType)) {
            qw.and(SysReviewTask::getSourceType).eq(sourceType.trim());
        }
        return reviewTaskMapper.selectCountByQuery(qw);
    }

    private void resolve(Long id, String status, AdminReviewResolveDTO dto, Long operatorId) {
        SysReviewTask task = requireTask(id);
        if (!SysReviewTask.STATUS_PENDING.equals(task.getStatus())) {
            throw new CustomException(400, "review already resolved");
        }

        boolean approved = SysReviewTask.STATUS_APPROVED.equals(status);
        boolean isGroupTarget = SysReviewTask.TARGET_GROUP.equals(task.getTargetType());
        String userAction = approved && !isGroupTarget
                ? normalizeUserAction(dto != null ? dto.getUserAction() : null) : "none";
        String groupAction = approved && isGroupTarget
                ? normalizeGroupAction(dto != null ? dto.getGroupAction() : null) : "none";
        String contentAction = normalizeContentAction(dto != null ? dto.getContentAction() : null);
        if (!approved && !"delete".equals(contentAction)) {
            contentAction = "none";
        }

        String appliedContent = "none";
        String appliedUser = "none";
        String appliedGroup = "none";
        if ("delete".equals(contentAction)) {
            appliedContent = applyContentAction(task);
        }
        if (approved) {
            if (!"none".equals(groupAction)) {
                appliedGroup = applyGroupAction(task, groupAction, dto != null ? dto.getResolution() : null, operatorId);
            }
            if (!"none".equals(userAction)) {
                appliedUser = applyUserAction(task, userAction, dto != null ? dto.getResolution() : null, operatorId);
            }
        }

        Date now = new Date();
        task.setStatus(status);
        task.setResolution(buildResolution(
                dto != null ? dto.getResolution() : null, appliedContent, appliedUser, appliedGroup));
        task.setResolvedBy(operatorId);
        task.setResolvedAt(now);
        task.setUpdateTime(now);
        reviewTaskMapper.update(task);

        afterTaskResolved(task, status, operatorId);
    }

    private void afterTaskResolved(SysReviewTask task, String status, Long operatorId) {
        if (task.getFeedbackId() != null) {
            Feedback feedback = feedbackMapper.selectOneById(task.getFeedbackId());
            if (feedback != null && !"closed".equals(feedback.getStatus())) {
                feedback.setStatus("closed");
                if (StringUtils.hasText(task.getResolution())) {
                    feedback.setReply(task.getResolution());
                    feedback.setReplyTime(task.getResolvedAt() != null ? task.getResolvedAt() : new Date());
                }
                feedbackMapper.update(feedback);
            }
        }
        clearSensitiveAlertHint(task);
        notifyReporter(task, status, operatorId);
        publishAdminEvent("review_resolved", task.getId());
    }

    private String normalizeUserAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "none";
        }
        String action = raw.trim().toLowerCase();
        if ("none".equals(action) || "freeze".equals(action) || "ban".equals(action)) {
            return action;
        }
        throw new CustomException(400, "用户处置动作无效，仅支持 none / freeze / ban");
    }

    private String normalizeContentAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "none";
        }
        String action = raw.trim().toLowerCase();
        if ("none".equals(action) || "delete".equals(action)) {
            return action;
        }
        throw new CustomException(400, "内容处置动作无效，仅支持 none / delete");
    }

    private String normalizeGroupAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "none";
        }
        String action = raw.trim().toLowerCase();
        if ("none".equals(action) || "dissolve".equals(action)
                || "freeze_owner".equals(action) || "ban_owner".equals(action)) {
            return action;
        }
        throw new CustomException(400, "群处置动作无效，仅支持 none / dissolve / freeze_owner / ban_owner");
    }

    private String applyUserAction(SysReviewTask task, String userAction, String resolution, Long operatorId) {
        Long subjectId = resolveSubjectUserId(task);
        if (subjectId == null) {
            throw new CustomException(400, "该审核任务没有关联用户，无法处罚");
        }
        return punishUser(subjectId, userAction, resolution, task, operatorId);
    }

    private String applyGroupAction(SysReviewTask task, String groupAction, String resolution, Long operatorId) {
        Long groupId = parseLongId(task.getTargetId());
        if (groupId == null) {
            throw new CustomException(400, "群 ID 无效，无法处置");
        }
        ImConversation group = conversationMapper.selectOneById(groupId);
        if (group == null || group.getType() == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在或已解散");
        }
        if ("dissolve".equals(groupAction)) {
            groupService.adminDissolveGroup(groupId, operatorId);
            return "dissolve";
        }
        Long ownerId = group.getOwnerId();
        if (ownerId == null) {
            throw new CustomException(400, "群主信息不存在，无法处罚");
        }
        try {
            String mapped = "freeze_owner".equals(groupAction) ? "freeze" : "ban";
            punishUser(ownerId, mapped, resolution, task, operatorId);
            return groupAction;
        } catch (CustomException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("已被封禁")) {
                throw new CustomException(ex.getCode(), "群主已被封禁，无需重复操作");
            }
            if (msg.contains("已被冻结") || msg.contains("冻结或封禁")) {
                throw new CustomException(ex.getCode(), "群主已处于冻结或封禁状态，无需重复操作");
            }
            throw ex;
        }
    }

    private String punishUser(Long subjectId, String userAction, String resolution, SysReviewTask task, Long operatorId) {
        String reason = StringUtils.hasText(resolution)
                ? resolution.trim()
                : ("审核处置: " + (task.getTitle() == null ? task.getId() : task.getTitle()));
        AdminUserActionDTO actionDTO = new AdminUserActionDTO();
        actionDTO.setReason(reason.length() > 255 ? reason.substring(0, 255) : reason);

        if ("freeze".equals(userAction)) {
            if (!rbacService.hasPermission(operatorId, "admin:user:freeze")) {
                throw new CustomException(403, "无冻结用户权限");
            }
            adminUserService.freeze(subjectId, actionDTO, operatorId);
            return "freeze";
        }
        if (!rbacService.hasPermission(operatorId, "admin:user:ban")) {
            throw new CustomException(403, "无封禁用户权限");
        }
        adminUserService.ban(subjectId, actionDTO, operatorId);
        return "ban";
    }

    private void clearSensitiveAlertHint(SysReviewTask task) {
        if (task == null || !SysReviewTask.SOURCE_SENSITIVE.equals(task.getSourceType())) {
            return;
        }
        if (!SysReviewTask.TARGET_MESSAGE.equals(task.getTargetType())) {
            return;
        }
        Long messageId = parseLongId(task.getTargetId());
        Long subjectId = resolveSubjectUserId(task);
        if (messageId == null || subjectId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", String.valueOf(messageId));
        if (StringUtils.hasText(task.getContentSnapshot())) {
            Matcher m = Pattern.compile("(?m)^会话ID:\\s*(.+)$").matcher(task.getContentSnapshot());
            if (m.find()) {
                payload.put("conversationId", m.group(1).trim());
            }
        }
        imPushService.pushToUser(subjectId, "sensitive_alert_clear", payload);
    }

    private static Long parseLongId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            String id = raw.trim();
            int colon = id.indexOf(':');
            if (colon > 0) {
                id = id.substring(0, colon);
            }
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String applyContentAction(SysReviewTask task) {
        String targetType = task.getTargetType() == null ? "" : task.getTargetType().trim();
        String targetId = task.getTargetId();
        if (!StringUtils.hasText(targetId)) {
            throw new CustomException(400, "该审核任务没有可删除的内容目标");
        }
        try {
            switch (targetType) {
                case SysReviewTask.TARGET_MESSAGE -> {
                    MessageVO recalled = chatService.adminForceRecallMessage(Long.parseLong(targetId.trim()));
                    if (recalled != null) {
                        imPushService.pushRecallToConversationMembers(recalled);
                    }
                    return "delete_message";
                }
                case SysReviewTask.TARGET_MOMENT -> {
                    momentsService.adminDeletePost(Long.parseLong(targetId.trim()));
                    return "delete_moment";
                }
                case SysReviewTask.TARGET_MOMENT_COMMENT -> {
                    momentsService.adminDeleteComment(Long.parseLong(targetId.trim()));
                    return "delete_moment_comment";
                }
                case SysReviewTask.TARGET_SHORT_VIDEO -> {
                    shortVideoService.adminDeletePost(Long.parseLong(targetId.trim()));
                    return "delete_short_video";
                }
                case SysReviewTask.TARGET_SHORT_VIDEO_COMMENT -> {
                    shortVideoService.adminDeleteComment(Long.parseLong(targetId.trim()));
                    return "delete_short_video_comment";
                }
                case SysReviewTask.TARGET_ANNOUNCEMENT -> {
                    groupAnnouncementService.adminDelete(Long.parseLong(targetId.trim()));
                    return "delete_announcement";
                }
                case SysReviewTask.TARGET_GROUP_FILE -> {
                    groupAssetService.adminDelete(Long.parseLong(targetId.trim()));
                    return "delete_group_file";
                }
                case SysReviewTask.TARGET_FAVORITE -> {
                    favoriteService.adminDelete(Long.parseLong(targetId.trim()));
                    return "delete_favorite";
                }
                default -> throw new CustomException(400,
                        "目标类型 " + targetType + " 不支持删除内容，请仅对消息/动态/评论/公告/群文件/收藏使用内容删除");
            }
        } catch (NumberFormatException ex) {
            throw new CustomException(400, "目标 ID 无效，无法删除内容");
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("审核删除内容失败 reviewId={} target={}:{}", task.getId(), targetType, targetId, ex);
            throw new CustomException(400, "删除内容失败: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
        }
    }

    private String buildResolution(String resolution, String appliedContent, String appliedUser, String appliedGroup) {
        String base = StringUtils.hasText(resolution) ? resolution.trim() : "";
        List<String> suffixes = new ArrayList<>();
        if ("delete_message".equals(appliedContent)) {
            suffixes.add("[已撤回消息]");
        } else if ("delete_moment".equals(appliedContent)) {
            suffixes.add("[已删除动态]");
        } else if ("delete_moment_comment".equals(appliedContent)) {
            suffixes.add("[已删除评论]");
        } else if ("delete_short_video".equals(appliedContent)) {
            suffixes.add("[已删除短视频]");
        } else if ("delete_short_video_comment".equals(appliedContent)) {
            suffixes.add("[已删除短视频评论]");
        } else if ("delete_announcement".equals(appliedContent)) {
            suffixes.add("[已删除公告]");
        } else if ("delete_group_file".equals(appliedContent)) {
            suffixes.add("[已删除群文件]");
        } else if ("delete_favorite".equals(appliedContent)) {
            suffixes.add("[已删除收藏]");
        }
        if ("freeze".equals(appliedUser)) {
            suffixes.add("[同时冻结用户]");
        } else if ("ban".equals(appliedUser)) {
            suffixes.add("[同时封禁用户]");
        }
        if ("dissolve".equals(appliedGroup)) {
            suffixes.add("[已解散群聊]");
        } else if ("freeze_owner".equals(appliedGroup)) {
            suffixes.add("[已冻结群主]");
        } else if ("ban_owner".equals(appliedGroup)) {
            suffixes.add("[已封禁群主]");
        }
        if (suffixes.isEmpty()) {
            return StringUtils.hasText(base) ? base : null;
        }
        String merged = (StringUtils.hasText(base) ? base + " " : "") + String.join(" ", suffixes);
        return merged.length() > 1000 ? merged.substring(0, 1000) : merged;
    }

    private void notifyReporter(SysReviewTask task, String status, Long operatorId) {
        Long reporterId = task.getReporterUserId();
        if (reporterId == null) {
            return;
        }
        boolean approved = SysReviewTask.STATUS_APPROVED.equals(status);
        String type = approved ? "review_approved" : "review_rejected";
        String title = approved ? "举报已处理" : "举报未通过";
        StringBuilder content = new StringBuilder();
        content.append("【").append(title).append("】\n");
        content.append("标题：").append(abbreviate(task.getTitle(), 40)).append('\n');
        if (StringUtils.hasText(task.getResolution())) {
            content.append("处理意见：").append(abbreviate(task.getResolution(), 200));
        } else {
            content.append("详情：")
                    .append(approved
                            ? "我们已核实并完成处理，感谢你的反馈"
                            : "经核实暂未认定违规，如有更多证据可再次举报");
        }
        String body = content.toString();
        Long relatedId = task.getFeedbackId() != null ? task.getFeedbackId() : task.getId();
        notificationService.create(
                reporterId,
                operatorId,
                OFFICIAL_SENDER,
                null,
                type,
                relatedId,
                body
        );
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("relatedId", String.valueOf(relatedId));
        payload.put("content", body);
        imPushService.pushToUser(reporterId, "notification_refresh", payload);
    }

    private void publishAdminEvent(String type, Long reviewId) {
        adminEventPublisher.publishToUsers(type, reviewId, adminAudienceService.reviewOperatorUserIds());
    }

    private void afterReviewCreated(SysReviewTask task) {
        publishAdminEvent("review_created", task.getId());
        approvalFlowEngine.tryAutoStartForReview(task, null);
    }

    private SysReviewTask buildFromFeedback(Feedback feedback) {
        String content = feedback.getContent() == null ? "" : feedback.getContent();
        Matcher prefix = REPORT_PREFIX.matcher(content);
        String reportKind = prefix.find() ? prefix.group(1) : "内容";
        String targetType;
        String targetId = null;
        if (reportKind.contains("短视频评论")) {
            targetType = SysReviewTask.TARGET_SHORT_VIDEO_COMMENT;
            Matcher commentLine = COMMENT_ID_LINE.matcher(content);
            if (commentLine.find()) {
                targetId = commentLine.group(1).trim();
            }
        } else if (reportKind.contains("短视频")) {
            targetType = SysReviewTask.TARGET_SHORT_VIDEO;
            Matcher postLine = POST_ID_LINE.matcher(content);
            if (postLine.find()) {
                targetId = postLine.group(1).trim();
            }
        } else if (reportKind.contains("群")) {
            targetType = SysReviewTask.TARGET_GROUP;
            Matcher gid = GROUP_ID_LINE.matcher(content);
            if (gid.find()) {
                targetId = gid.group(1).trim();
            }
        } else {
            targetType = SysReviewTask.TARGET_USER;
            Matcher uid = USER_ID_LINE.matcher(content);
            if (uid.find()) {
                targetId = uid.group(1).trim();
            }
        }
        String title = "举报" + reportKind;
        if (content.length() > 40) {
            title = content.lines().findFirst().orElse(title);
            if (title.length() > 64) {
                title = title.substring(0, 64);
            }
        }
        Date now = new Date();
        Long subjectUserId = subjectUserIdFromTarget(targetType, targetId);
        if (subjectUserId == null) {
            subjectUserId = subjectUserIdFromContent(content);
        }
        String riskLevel = reviewRiskScoringService.elevateLevel("medium", subjectUserId);
        return SysReviewTask.builder()
                .sourceType(SysReviewTask.SOURCE_REPORT)
                .targetType(targetType)
                .targetId(targetId)
                .reporterUserId(feedback.getUserId())
                .reporterUsername(feedback.getUsername())
                .title(title)
                .contentSnapshot(content)
                .riskLevel(riskLevel)
                .status(SysReviewTask.STATUS_PENDING)
                .feedbackId(feedback.getId())
                .escalationCount(0)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private static boolean isReportContent(String content) {
        return StringUtils.hasText(content) && content.trim().startsWith("[举报");
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private AdminReviewVO toVO(SysReviewTask task) {
        return AdminReviewVO.builder()
                .id(task.getId())
                .sourceType(task.getSourceType())
                .targetType(task.getTargetType())
                .targetId(task.getTargetId())
                .subjectUserId(resolveSubjectUserId(task))
                .reporterUserId(task.getReporterUserId())
                .reporterUsername(task.getReporterUsername())
                .title(task.getTitle())
                .contentSnapshot(task.getContentSnapshot())
                .evidenceUrls(resolveEvidenceUrls(task.getContentSnapshot()))
                .riskLevel(task.getRiskLevel())
                .status(task.getStatus())
                .feedbackId(task.getFeedbackId())
                .resolution(task.getResolution())
                .resolvedBy(task.getResolvedBy())
                .resolvedAt(task.getResolvedAt())
                .createTime(task.getCreateTime())
                .overdue(ReviewEscalationServiceImpl.isOverdue(task, linkxProperties))
                .escalated(isEscalated(task))
                .escalationCount(normalizeEscalationCount(task.getEscalationCount()))
                .escalatedAt(task.getEscalatedAt())
                .approvalInstanceId(task.getApprovalInstanceId())
                .approvalStatus(resolveApprovalStatus(task.getApprovalInstanceId()))
                .build();
    }

    private String resolveApprovalStatus(Long approvalInstanceId) {
        if (approvalInstanceId == null) {
            return null;
        }
        SysApprovalInstance instance = approvalInstanceMapper.selectOneById(approvalInstanceId);
        return instance != null ? instance.getStatus() : null;
    }

    private static boolean isEscalated(SysReviewTask task) {
        return task != null && task.getEscalationCount() != null && task.getEscalationCount() > 0;
    }

    private static int normalizeEscalationCount(Integer count) {
        return count == null || count < 0 ? 0 : count;
    }

    private static Long subjectUserIdFromTarget(String targetType, String targetId) {
        if (SysReviewTask.TARGET_USER.equals(targetType) && StringUtils.hasText(targetId)) {
            try {
                return Long.parseLong(targetId.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long subjectUserIdFromContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher author = AUTHOR_ID_LINE.matcher(content);
        if (author.find()) {
            try {
                return Long.parseLong(author.group(1).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Matcher uid = USER_ID_LINE.matcher(content);
        if (uid.find()) {
            try {
                return Long.parseLong(uid.group(1).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long resolveSubjectUserId(SysReviewTask task) {
        if (task == null) {
            return null;
        }
        if (SysReviewTask.TARGET_USER.equals(task.getTargetType()) && StringUtils.hasText(task.getTargetId())) {
            try {
                return Long.parseLong(task.getTargetId().trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (StringUtils.hasText(task.getTargetId())) {
            try {
                long tid = Long.parseLong(task.getTargetId().trim());
                if (SysReviewTask.TARGET_GROUP_FILE.equals(task.getTargetType())) {
                    GroupAsset asset = groupAssetMapper.selectOneById(tid);
                    if (asset != null && asset.getUploaderId() != null) {
                        return asset.getUploaderId();
                    }
                } else if (SysReviewTask.TARGET_FAVORITE.equals(task.getTargetType())) {
                    Favorite fav = favoriteMapper.selectOneById(tid);
                    if (fav != null && fav.getUserId() != null) {
                        return fav.getUserId();
                    }
                } else if (SysReviewTask.TARGET_SHORT_VIDEO.equals(task.getTargetType())) {
                    Long authorId = shortVideoService.findPostAuthorId(tid);
                    if (authorId != null) {
                        return authorId;
                    }
                } else if (SysReviewTask.TARGET_SHORT_VIDEO_COMMENT.equals(task.getTargetType())) {
                    Long authorId = shortVideoService.findCommentAuthorId(tid);
                    if (authorId != null) {
                        return authorId;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        String content = task.getContentSnapshot();
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher author = AUTHOR_ID_LINE.matcher(content);
        if (author.find()) {
            try {
                return Long.parseLong(author.group(1).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Matcher uid = USER_ID_LINE.matcher(content);
        if (uid.find()) {
            try {
                return Long.parseLong(uid.group(1).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectOneById(userId);
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    /** 从举报正文解析证据 object key 并签发可访问 URL */
    private List<String> resolveEvidenceUrls(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        Matcher m = EVIDENCE_KEY_LINE.matcher(content);
        while (m.find()) {
            String key = m.group(1).trim();
            if (key.contains("..") || key.startsWith("/") || key.contains("://")) {
                continue;
            }
            try {
                String url = mediaUrlService.resolveFile(key);
                if (StringUtils.hasText(url)) {
                    urls.add(url);
                }
            } catch (Exception ignored) {
                // 单张失败不影响其它证据
            }
        }
        return urls;
    }

    private SysReviewTask requireTask(Long id) {
        SysReviewTask task = reviewTaskMapper.selectOneById(id);
        if (task == null) {
            throw new CustomException(404, "review not found");
        }
        return task;
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
