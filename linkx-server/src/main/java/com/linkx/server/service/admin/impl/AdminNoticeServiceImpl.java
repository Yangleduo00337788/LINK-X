package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminNoticeDTO;
import com.linkx.server.controller.admin.dto.AdminNoticeQueryDTO;
import com.linkx.server.controller.admin.vo.AdminNoticeVO;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.admin.SysAdminNotice;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.admin.SysAdminNoticeMapper;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.admin.AdminEventPublisher;
import com.linkx.server.service.admin.AdminNoticeService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNoticeServiceImpl implements AdminNoticeService {

    public static final String NOTICE_TYPE = "notice_published";
    public static final String NOTICE_UNPUBLISHED_TYPE = "notice_unpublished";
    private static final String OFFICIAL_SENDER = "LinkX\u5B98\u65B9";

    private static final Set<String> STATUSES = Set.of(
            SysAdminNotice.STATUS_DRAFT,
            SysAdminNotice.STATUS_PUBLISHED,
            SysAdminNotice.STATUS_UNPUBLISHED
    );
    private static final Set<String> TARGETS = Set.of(
            SysAdminNotice.TARGET_ADMIN,
            SysAdminNotice.TARGET_CLIENT
    );

    private final SysAdminNoticeMapper noticeMapper;
    private final SysUserMapper sysUserMapper;
    private final MessageNotificationService notificationService;
    private final ImMessagePushService imPushService;
    private final AdminEventPublisher adminEventPublisher;

    @Override
    public PageResultVO<AdminNoticeVO> list(AdminNoticeQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = QueryWrapper.create()
                .where(SysAdminNotice::getDeleted).eq(0);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysAdminNotice::getTitle).like(kw)
                        .or(SysAdminNotice::getContent).like(kw);
            });
        }
        if (StringUtils.hasText(query.getNoticeStatus())) {
            String status = query.getNoticeStatus().trim();
            if (STATUSES.contains(status)) {
                qw.and(SysAdminNotice::getStatus).eq(status);
            }
        }
        if (StringUtils.hasText(query.getTargetSide())) {
            String side = query.getTargetSide().trim();
            if (TARGETS.contains(side)) {
                qw.and(SysAdminNotice::getTargetSide).eq(side);
            }
        }
        if (query.getStartTime() != null) {
            qw.and(SysAdminNotice::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysAdminNotice::getCreateTime).le(new Date(query.getEndTime()));
        }
        qw.orderBy(SysAdminNotice::getUpdateTime, false);
        long total = noticeMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<AdminNoticeVO> items = noticeMapper.selectListByQuery(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    @Override
    public AdminNoticeVO detail(Long id) {
        return toVO(requireNotice(id));
    }

    @Override
    @Transactional
    public AdminNoticeVO create(AdminNoticeDTO dto, Long operatorId) {
        Date now = new Date();
        String targetSide = normalizeTargetSide(dto.getTargetSide());
        SysAdminNotice entity = SysAdminNotice.builder()
                .title(normalizeTitle(dto.getTitle()))
                .content(normalizeContent(dto.getContent()))
                .targetSide(targetSide)
                .status(SysAdminNotice.STATUS_DRAFT)
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        noticeMapper.insert(entity);
        // 列表协同刷新（两端都发 list 事件）；公告横幅仅 admin 端
        publishListEvent("notice_created", entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminNoticeVO update(Long id, AdminNoticeDTO dto, Long operatorId) {
        SysAdminNotice entity = requireNotice(id);
        if (SysAdminNotice.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published notice cannot be edited, unpublish first");
        }
        entity.setTitle(normalizeTitle(dto.getTitle()));
        entity.setContent(normalizeContent(dto.getContent()));
        // 草稿允许改目标端；已发布不可改（上面已拦）
        if (StringUtils.hasText(dto.getTargetSide())) {
            entity.setTargetSide(normalizeTargetSide(dto.getTargetSide()));
        }
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        noticeMapper.update(entity);
        publishListEvent("notice_updated", entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        SysAdminNotice entity = requireNotice(id);
        if (SysAdminNotice.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "published notice cannot be deleted, unpublish first");
        }
        entity.setDeleted(1);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(new Date());
        noticeMapper.update(entity);
        publishListEvent("notice_deleted", entity);
    }

    @Override
    @Transactional
    public AdminNoticeVO publish(Long id, Long operatorId) {
        SysAdminNotice entity = requireNotice(id);
        if (SysAdminNotice.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "notice already published");
        }
        Date now = new Date();
        entity.setStatus(SysAdminNotice.STATUS_PUBLISHED);
        entity.setPublishedAt(now);
        entity.setPublishedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        noticeMapper.update(entity);

        String side = normalizeStoredSide(entity.getTargetSide());
        if (SysAdminNotice.TARGET_CLIENT.equals(side)) {
            // 仅客户端：写入 LinkX 官方通知 + IM WS，不向管理端发公告横幅
            broadcastToClients(entity, operatorId, buildOfficialContent(entity));
            publishListEvent("notice_published", entity);
        } else {
            // 仅管理端：SSE 实时公告横幅，不推客户端
            publishAdminBulletin(entity);
            publishListEvent("notice_published", entity);
        }
        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminNoticeVO unpublish(Long id, Long operatorId) {
        SysAdminNotice entity = requireNotice(id);
        if (!SysAdminNotice.STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new CustomException(400, "only published notice can be unpublished");
        }
        Date now = new Date();
        entity.setStatus(SysAdminNotice.STATUS_UNPUBLISHED);
        entity.setUpdatedBy(operatorId);
        entity.setUpdateTime(now);
        noticeMapper.update(entity);

        String side = normalizeStoredSide(entity.getTargetSide());
        if (SysAdminNotice.TARGET_CLIENT.equals(side)) {
            // 撤回已推送到「LinkX官方」的通知记录，并通知在线客户端刷新
            int removed = notificationService.deleteByTypeAndRelatedId(NOTICE_TYPE, entity.getId());
            log.info("客户端公告已撤回官方通知: noticeId={}, removed={}", entity.getId(), removed);
            recallClientNotifications(entity);
        } else {
            // 通知管理端收件箱移除该公告
            recallAdminBulletin(entity);
        }
        publishListEvent("notice_unpublished", entity);
        return toVO(entity);
    }

    @Override
    public PageResultVO<AdminNoticeVO> listInbox(AdminNoticeQueryDTO query) {
        AdminNoticeQueryDTO inboxQuery = query != null ? query : new AdminNoticeQueryDTO();
        inboxQuery.setTargetSide(SysAdminNotice.TARGET_ADMIN);
        inboxQuery.setNoticeStatus(SysAdminNotice.STATUS_PUBLISHED);
        return list(inboxQuery);
    }

    /** 管理端公告：实时横幅 + 收件箱刷新（不影响客户端） */
    private void publishAdminBulletin(SysAdminNotice entity) {
        adminEventPublisher.publish(
                "admin_notice_published",
                entity.getId(),
                "{\"title\":\"" + escapeJson(entity.getTitle())
                        + "\",\"targetSide\":\"admin\",\"content\":\""
                        + escapeJson(abbreviate(entity.getContent(), 200)) + "\"}"
        );
    }

    /** 管理端公告下线：通知收件箱撤回 */
    private void recallAdminBulletin(SysAdminNotice entity) {
        adminEventPublisher.publish(
                "admin_notice_unpublished",
                entity.getId(),
                "{\"title\":\"" + escapeJson(entity.getTitle())
                        + "\",\"targetSide\":\"admin\"}"
        );
    }

    /** 客户端公告下线：WS 刷新，前端按 relatedId 移除官方通知 */
    private void recallClientNotifications(SysAdminNotice notice) {
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("type", NOTICE_UNPUBLISHED_TYPE);
        payload.put("relatedId", String.valueOf(notice.getId()));
        try {
            imPushService.pushToAllOnline("notification_refresh", payload);
        } catch (Exception e) {
            log.warn("客户端公告撤回 WS 广播失败: noticeId={}, err={}", notice.getId(), e.getMessage());
        }
    }

    /** 列表协同：任意目标端变更都刷新管理端表格，但不等于公告互通 */
    private void publishListEvent(String type, SysAdminNotice entity) {
        String side = normalizeStoredSide(entity.getTargetSide());
        adminEventPublisher.publish(
                type,
                entity.getId(),
                "{\"title\":\"" + escapeJson(entity.getTitle())
                        + "\",\"targetSide\":\"" + side + "\"}"
        );
    }

    private void broadcastToClients(SysAdminNotice notice, Long operatorId, String content) {
        List<Long> userIds = sysUserMapper.selectListByQuery(
                        QueryWrapper.create().select(SysUser::getId)
                ).stream()
                .map(SysUser::getId)
                .filter(uid -> uid != null)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        int inserted = notificationService.createForUsers(
                userIds,
                operatorId,
                OFFICIAL_SENDER,
                null,
                NOTICE_TYPE,
                notice.getId(),
                content
        );
        log.info("客户端公告已写入官方通知: noticeId={}, users={}", notice.getId(), inserted);

        Map<String, Object> payload = new HashMap<>(4);
        payload.put("type", NOTICE_TYPE);
        payload.put("relatedId", String.valueOf(notice.getId()));
        payload.put("content", content);
        try {
            imPushService.pushToAllOnline("notification_refresh", payload);
        } catch (Exception e) {
            log.warn("客户端公告 WS 广播失败: noticeId={}, err={}", notice.getId(), e.getMessage());
        }
    }

    private static String buildOfficialContent(SysAdminNotice notice) {
        StringBuilder sb = new StringBuilder();
        sb.append("【系统公告】\n");
        sb.append("标题：").append(notice.getTitle()).append('\n');
        sb.append(abbreviate(notice.getContent(), 500));
        return sb.toString();
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "\u2026";
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String normalizeTargetSide(String side) {
        if (!StringUtils.hasText(side)) {
            throw new CustomException(400, "targetSide required");
        }
        String s = side.trim().toLowerCase();
        if (!TARGETS.contains(s)) {
            throw new CustomException(400, "invalid targetSide");
        }
        return s;
    }

    private static String normalizeStoredSide(String side) {
        if (SysAdminNotice.TARGET_ADMIN.equals(side)) {
            return SysAdminNotice.TARGET_ADMIN;
        }
        return SysAdminNotice.TARGET_CLIENT;
    }

    private static String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new CustomException(400, "title required");
        }
        return title.trim();
    }

    private static String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new CustomException(400, "content required");
        }
        return content.trim();
    }

    private AdminNoticeVO toVO(SysAdminNotice entity) {
        return AdminNoticeVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .targetSide(normalizeStoredSide(entity.getTargetSide()))
                .status(entity.getStatus())
                .publishedAt(entity.getPublishedAt())
                .publishedBy(entity.getPublishedBy())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private SysAdminNotice requireNotice(Long id) {
        SysAdminNotice entity = noticeMapper.selectOneById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new CustomException(404, "notice not found");
        }
        return entity;
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
