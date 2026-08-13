package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.dto.UpdateMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;
import com.linkx.server.entity.*;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.*;
import com.linkx.server.repository.MomentsCommentRepository;
import com.linkx.server.repository.MomentsPostRepository;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.linkx.server.service.ExternalMediaProxyService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.MomentsService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.common.SafeExternalUrl;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MomentsServiceImpl implements MomentsService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MomentsPostMapper postMapper;
    private final MomentsPostRepository postRepository;
    private final MomentsImageMapper imageMapper;
    private final MomentsLikeMapper likeMapper;
    private final MomentsCommentMapper commentMapper;
    private final MomentsCommentRepository commentRepository;
    private final SysUserMapper userMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final ExternalMediaProxyService externalMediaProxyService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;
    private final MessageNotificationService notificationService;
    private final MessageNotificationMapper notificationMapper;
    private final ImMessagePushService imPushService;
    private final ObjectMapper objectMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ObjectProvider<AdminReviewService> adminReviewService;
    private final MessageContentCipher messageContentCipher;
    private final LinkxProperties linkxProperties;

    @Override
    @Transactional
    public MomentsPostVO publish(Long userId, PublishMomentsDTO dto) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        boolean hasContent = dto.getContent() != null && !dto.getContent().isBlank();
        boolean hasImages = dto.getImages() != null && dto.getImages().stream()
                .anyMatch(u -> u != null && !u.isBlank());
        if (!hasContent && !hasImages) {
            throw new CustomException(400, "动态内容或媒体不能同时为空");
        }

        String rawContent = hasContent ? dto.getContent().trim() : "";
        SensitiveHit hit = filterSensitiveOrThrow(userId, rawContent, SysReviewTask.TARGET_MOMENT, null);
        String postContent = hit.text();

        // 处理 atUsers：序列化为 JSON 字符串
        String atUsersJson = null;
        List<Long> atUserIds = sanitizeMentions(dto.getAtUsers(), userId);
        if (!atUserIds.isEmpty()) {
            atUsersJson = toJsonString(atUserIds);
        }

        // 默认可见性为公开（0）
        Integer visibility = dto.getVisibility() != null ? dto.getVisibility() : 0;

        MomentsPost post = MomentsPost.builder()
                .userId(userId)
                .content(postContent)
                .location(dto.getLocation())
                .atUsers(atUsersJson)
                .visibility(visibility)
                .build();
        postRepository.insert(post);
        enqueueSensitiveAfterPersist(userId, SysReviewTask.TARGET_MOMENT, String.valueOf(post.getId()), hit);

        List<MomentsImage> savedImages = new ArrayList<>();
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            int order = 0;
            for (String imageUrl : dto.getImages()) {
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }
                MomentsImage image = MomentsImage.builder()
                        .postId(post.getId())
                        .url(normalizeAndAuthorizeMomentsMedia(userId, imageUrl))
                        .sortOrder(order++)
                        .build();
                imageMapper.insert(image);
                savedImages.add(image);
            }
        }

        // 发送提醒通知给被 @ 的用户
        if (!atUserIds.isEmpty()) {
            for (Long targetId : atUserIds) {
                try {
                    notificationService.create(
                            targetId,
                            userId,
                            user.getNickname(),
                            user.getAvatar(),
                            "moments_at",
                            post.getId(),
                            extractPostPreview(dto.getContent())
                    );
                    imPushService.pushToUser(targetId, "notification_refresh", Map.of("type", "moments_at"));
                } catch (Exception e) {
                    log.warn("发送提醒通知失败 postId={} targetId={}: {}", post.getId(), targetId, e.getMessage());
                }
            }
        }

        // 向所有好友推送新动态（让他们的朋友圈页面实时显示）
        MomentsPostVO postVO = toPostVO(post, user, savedImages, Collections.emptyList(), Collections.emptyList(), userId);
        pushNewMomentsToFriends(userId, postVO);

        return postVO;
    }

    /**
     * 向所有好友推送新发布的动态。
     * @param authorId 发布者 ID
     * @param postVO 动态内容
     */
    private void pushNewMomentsToFriends(Long authorId, MomentsPostVO postVO) {
        Set<Long> friendIds = getFriendIds(authorId);
        if (friendIds.isEmpty()) {
            return;
        }
        Map<String, Object> pushData = Map.of(
                "post", postVO
        );
        for (Long friendId : friendIds) {
            imPushService.pushToUser(friendId, "moments_new_post", pushData);
        }
    }

    @Override
    @Transactional
    public MomentsPostVO update(Long userId, Long postId, UpdateMomentsDTO dto) {
        MomentsPost post = postRepository.selectOneByQuery(
                QueryWrapper.create().eq("id", postId).eq("deleted", 0)
        );
        if (post == null) {
            throw new CustomException(404, "动态不存在");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new CustomException(403, "只能编辑自己的动态");
        }

        boolean hasContent = dto.getContent() != null && !dto.getContent().isBlank();
        boolean touchImages = dto.getImages() != null;
        if (!hasContent && !touchImages && dto.getLocation() == null
                && dto.getAtUsers() == null && dto.getVisibility() == null) {
            throw new CustomException(400, "没有可更新的内容");
        }

        if (dto.getContent() != null) {
            String raw = dto.getContent().trim();
            SensitiveHit hit = filterSensitiveOrThrow(
                    userId, raw, SysReviewTask.TARGET_MOMENT, String.valueOf(postId));
            post.setContent(hit.text());
            enqueueSensitiveAfterPersist(userId, SysReviewTask.TARGET_MOMENT, String.valueOf(postId), hit);
        }
        if (dto.getLocation() != null) {
            post.setLocation(dto.getLocation().isBlank() ? null : dto.getLocation().trim());
        }
        if (dto.getVisibility() != null) {
            post.setVisibility(dto.getVisibility());
        }
        if (dto.getAtUsers() != null) {
            List<Long> atUserIds = sanitizeMentions(dto.getAtUsers(), userId);
            post.setAtUsers(atUserIds.isEmpty() ? null : toJsonString(atUserIds));
        }
        postRepository.update(post);

        List<MomentsImage> savedImages;
        if (touchImages) {
            imageMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
            savedImages = new ArrayList<>();
            int order = 0;
            for (String imageUrl : dto.getImages()) {
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }
                MomentsImage image = MomentsImage.builder()
                        .postId(post.getId())
                        .url(normalizeAndAuthorizeMomentsMedia(userId, imageUrl))
                        .sortOrder(order++)
                        .build();
                imageMapper.insert(image);
                savedImages.add(image);
            }
        } else {
            savedImages = imageMapper.selectListByQuery(
                    QueryWrapper.create().eq("post_id", postId).orderBy("sort_order", true)
            );
        }

        SysUser user = userMapper.selectOneById(userId);
        List<MomentsLike> likes = likeMapper.selectListByQuery(
                QueryWrapper.create().eq("post_id", postId)
        );
        List<MomentsComment> comments = commentRepository.selectListByQuery(
                QueryWrapper.create().eq("post_id", postId).eq("deleted", 0).orderBy("create_time", true)
        );
        return toPostVO(post, user, savedImages, likes, comments, userId);
    }

    @Override
    public List<MomentsPostVO> list(Long userId, Long beforeId, Integer limit, String q) {
        Set<Long> friendIds = getFriendIds(userId);
        friendIds.add(userId);
        int pageSize = normalizeLimit(limit);
        boolean searching = q != null && !q.isBlank();

        QueryWrapper qw = QueryWrapper.create()
                .in("user_id", new ArrayList<>(friendIds))
                .eq("deleted", 0)
                .and("(IFNULL(visibility, 0) <> 2 OR user_id = ?)", userId)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(resolveFetchLimit(pageSize, searching));
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        applyContentSearch(qw, q);

        List<MomentsPostVO> result = buildPostList(qw, userId, null);
        return filterSearchResults(result, q, pageSize);
    }

    @Override
    public List<MomentsPostVO> listByUser(Long userId, Long targetUserId, Long beforeId, Integer limit, String q) {
        int pageSize = normalizeLimit(limit);
        boolean self = Objects.equals(userId, targetUserId);
        boolean friend = self || getFriendIds(userId).contains(targetUserId);

        QueryWrapper qw = QueryWrapper.create()
                .eq("user_id", targetUserId)
                .eq("deleted", 0);
        if (self) {
            // 本人可见全部（含私密）
        } else if (friend) {
            // 好友：公开 + 仅好友，不含私密
            qw.and("IFNULL(visibility, 0) <> 2");
        } else {
            // 陌生人：仅公开
            qw.and("IFNULL(visibility, 0) = 0");
        }
        qw.orderBy("create_time", false)
                .orderBy("id", false)
                .limit(resolveFetchLimit(pageSize, q != null && !q.isBlank()));
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        applyContentSearch(qw, q);

        SysUser targetUser = userMapper.selectOneById(targetUserId);
        List<MomentsPost> posts = postRepository.selectListByQuery(qw);
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        if (q != null && !q.isBlank() && messageContentCipher.isEnabled()) {
            posts = filterPostsByKeyword(posts, q, pageSize);
        }
        Map<Long, List<MomentsImage>> imagesMap = loadImages(posts);
        Map<Long, List<MomentsLike>> likesMap = loadLikes(posts);
        Map<Long, List<MomentsComment>> commentsMap = loadComments(posts);
        List<MomentsPostVO> result = new ArrayList<>();
        for (MomentsPost post : posts) {
            if (!canViewPost(post, userId)) {
                continue;
            }
            result.add(toPostVO(post,
                    targetUser,
                    imagesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    likesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    commentsMap.getOrDefault(post.getId(), Collections.emptyList()),
                    userId));
        }
        return result;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 50);
    }

    private void applyContentSearch(QueryWrapper qw, String q) {
        if (q == null || q.isBlank() || messageContentCipher.isEnabled()) {
            return;
        }
        String keyword = q.trim();
        if (keyword.length() > 64) {
            keyword = keyword.substring(0, 64);
        }
        qw.and("content LIKE ?", "%" + escapeLike(keyword) + "%");
    }

    private int resolveFetchLimit(int pageSize, boolean searching) {
        if (searching && messageContentCipher.isEnabled()) {
            return linkxProperties.getMessageEncryption().getSearchScanLimit();
        }
        return pageSize;
    }

    private List<MomentsPostVO> filterSearchResults(List<MomentsPostVO> posts, String q, int pageSize) {
        if (q == null || q.isBlank() || !messageContentCipher.isEnabled()) {
            return posts;
        }
        String keyword = q.trim().toLowerCase();
        return posts.stream()
                .filter(vo -> matchesMomentsKeyword(vo.getContent(), vo.getLocation(), keyword))
                .limit(pageSize)
                .toList();
    }

    private List<MomentsPost> filterPostsByKeyword(List<MomentsPost> posts, String q, int pageSize) {
        String keyword = q.trim().toLowerCase();
        return posts.stream()
                .filter(post -> matchesMomentsKeyword(post.getContent(), post.getLocation(), keyword))
                .limit(pageSize)
                .toList();
    }

    private static boolean matchesMomentsKeyword(String content, String location, String keyword) {
        if (content != null && content.toLowerCase().contains(keyword)) {
            return true;
        }
        return location != null && location.toLowerCase().contains(keyword);
    }

    private List<MomentsPostVO> buildPostList(QueryWrapper qw, Long userId, SysUser fixedAuthor) {
        List<MomentsPost> posts = postRepository.selectListByQuery(qw);
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<MomentsImage>> imagesMap = loadImages(posts);
        Map<Long, List<MomentsLike>> likesMap = loadLikes(posts);
        Map<Long, List<MomentsComment>> commentsMap = loadComments(posts);
        Map<Long, SysUser> userMap = fixedAuthor != null
                ? Map.of(fixedAuthor.getId(), fixedAuthor)
                : loadUsers(posts, commentsMap);

        List<MomentsPostVO> result = new ArrayList<>();
        for (MomentsPost post : posts) {
            if (!canViewPost(post, userId)) {
                continue;
            }
            SysUser author = fixedAuthor != null ? fixedAuthor : userMap.get(post.getUserId());
            result.add(toPostVO(post,
                    author,
                    imagesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    likesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    commentsMap.getOrDefault(post.getId(), Collections.emptyList()),
                    userId));
        }
        return result;
    }

    /**
     * 可见性校验：0=公开，1=仅好友，2=私密仅作者可见。
     * 点赞/评论等单条交互必须走完整好友校验，不能依赖「列表已过滤」。
     */
    private boolean canViewPost(MomentsPost post, Long viewerId) {
        if (post == null || viewerId == null) {
            return false;
        }
        if (Objects.equals(post.getUserId(), viewerId)) {
            return true;
        }
        int visibility = post.getVisibility() == null ? 0 : post.getVisibility();
        if (visibility == 2) {
            return false;
        }
        if (visibility == 1) {
            return getFriendIds(viewerId).contains(post.getUserId());
        }
        return true;
    }

    /** 点赞/评论前校验：动态须存在且当前用户可见 */
    private MomentsPost assertCanInteract(Long userId, Long postId) {
        MomentsPost post = postRepository.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", postId)
                        .eq("deleted", 0)
        );
        if (post == null) {
            throw new CustomException(404, "动态不存在");
        }
        if (!canViewPost(post, userId)) {
            throw new CustomException(403, "无权查看该动态");
        }
        return post;
    }

    @Override
    @Transactional
    public void like(Long userId, Long postId) {
        MomentsPost post = assertCanInteract(userId, postId);

        // 已点赞则直接返回（允许赞自己的动态）
        MomentsLike existing = likeMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("post_id", postId)
                        .eq("user_id", userId)
        );
        if (existing != null) {
            return;
        }

        // 取消赞是逻辑删除，unique(post_id,user_id) 仍占用；先物理清掉残留再插入
        LogicDeleteManager.execWithoutLogicDelete(() ->
                likeMapper.deleteByQuery(
                        QueryWrapper.create()
                                .eq("post_id", postId)
                                .eq("user_id", userId)
                )
        );

        try {
            likeMapper.insert(MomentsLike.builder()
                    .postId(postId)
                    .userId(userId)
                    .build());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引冲突兜底：并发 like 命中唯一键，静默返回实现幂等
            return;
        }

        // 给动态作者推送消息通知(不通知自己赞自己)
        if (!post.getUserId().equals(userId)) {
            SysUser liker = userMapper.selectOneById(userId);
            String likerName = liker != null ? liker.getNickname() : null;
            String content = extractPostPreview(post.getContent());
            try {
                notificationService.create(
                        post.getUserId(),
                        userId,
                        likerName,
                        liker != null ? liker.getAvatar() : null,
                        "moments_like",
                        postId,
                        content
                );
                imPushService.pushToUser(post.getUserId(), "notification_refresh", Map.of("type", "moments_like"));
            } catch (Exception e) {
                log.warn("发送点赞通知失败 postId={} userId={}: {}", postId, userId, e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        likeMapper.deleteByQuery(
                QueryWrapper.create()
                        .eq("post_id", postId)
                        .eq("user_id", userId)
        );
    }

    @Override
    @Transactional
    public MomentsCommentVO comment(Long userId, Long postId, CommentMomentsDTO dto) {
        MomentsPost post = assertCanInteract(userId, postId);

        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        // 解析 mentions:优先用 DTO 中传入的列表;缺省时尝试从 content 中解析 @昵称
        List<Long> mentionIds = sanitizeMentions(dto.getMentions(), userId);
        if (mentionIds.isEmpty() && dto.getContent() != null) {
            mentionIds = parseMentionedUserIds(dto.getContent(), userId);
        }
        String mentionJson = mentionIds.isEmpty() ? null : toJsonString(mentionIds);

        // 引用父评论时校验 parentId 属于同一动态，防止跨动态越权读取父评论
        if (dto.getParentId() != null) {
            MomentsComment parent = commentRepository.selectOneById(dto.getParentId());
            if (parent == null) {
                throw new CustomException(404, "引用的父评论不存在");
            }
            if (!postId.equals(parent.getPostId())) {
                throw new CustomException(403, "引用的父评论不属于该动态");
            }
        }

        String rawComment = dto.getContent() == null ? "" : dto.getContent().trim();
        SensitiveHit commentHit = filterSensitiveOrThrow(
                userId, rawComment, SysReviewTask.TARGET_MOMENT_COMMENT, null);

        MomentsComment comment = MomentsComment.builder()
                .postId(postId)
                .userId(userId)
                .content(commentHit.text())
                .parentId(dto.getParentId())
                .mentions(mentionJson)
                .build();
        commentRepository.insert(comment);
        enqueueSensitiveAfterPersist(
                userId, SysReviewTask.TARGET_MOMENT_COMMENT, String.valueOf(comment.getId()), commentHit);

        // 给动态作者推送消息通知
        if (post != null && !post.getUserId().equals(userId)) {
            try {
                notificationService.create(
                        post.getUserId(),
                        userId,
                        user.getNickname(),
                        user.getAvatar(),
                        "moments_comment",
                        postId,
                        truncate(dto.getContent(), 100)
                );
                imPushService.pushToUser(post.getUserId(), "notification_refresh", Map.of("type", "moments_comment"));
            } catch (Exception e) {
                log.warn("发送评论通知失败 postId={} userId={}: {}", postId, userId, e.getMessage());
            }
        }

        // 给被 @ 的用户推送 mentions 通知(去重作者、自己)
        if (!mentionIds.isEmpty() && post != null) {
            Set<Long> notifyTargets = new LinkedHashSet<>(mentionIds);
            notifyTargets.remove(userId);
            notifyTargets.remove(post.getUserId());
            for (Long targetId : notifyTargets) {
                try {
                    notificationService.create(
                            targetId,
                            userId,
                            user.getNickname(),
                            user.getAvatar(),
                            "moments_mention",
                            postId,
                            truncate(dto.getContent(), 100)
                    );
                    imPushService.pushToUser(targetId, "notification_refresh", Map.of("type", "moments_mention"));
                } catch (Exception e) {
                    log.warn("发送@通知失败 postId={} userId={}: {}", postId, userId, e.getMessage());
                }
            }
        }

        return toCommentVO(comment, user, mentionIds);
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        MomentsComment comment = commentRepository.selectOneById(commentId);
        if (comment == null) {
            throw new CustomException(404, "评论不存在");
        }
        boolean isAuthor = comment.getUserId().equals(userId);
        boolean isPostOwner = false;
        if (!isAuthor && comment.getPostId() != null) {
            MomentsPost post = postMapper.selectOneById(comment.getPostId());
            isPostOwner = post != null && userId.equals(post.getUserId());
        }
        if (!isAuthor && !isPostOwner) {
            throw new CustomException(403, "无权删除此评论");
        }
        commentMapper.deleteById(commentId);
    }

    @Override
    @Transactional
    public void adminDeleteComment(Long commentId) {
        MomentsComment comment = commentRepository.selectOneById(commentId);
        if (comment == null) {
            throw new CustomException(404, "评论不存在");
        }
        commentMapper.deleteById(commentId);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long postId) {
        MomentsPost post = postRepository.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", postId)
                        .eq("deleted", 0)
        );
        if (post == null) {
            throw new CustomException(404, "动态不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new CustomException(403, "无权删除此动态");
        }
        forceDeletePost(postId);
    }

    @Override
    @Transactional
    public void adminDeletePost(Long postId) {
        MomentsPost post = postRepository.selectOneByQuery(
                QueryWrapper.create().eq("id", postId).eq("deleted", 0)
        );
        if (post == null) {
            // 兼容逻辑删除后的二次处置
            MomentsPost any = postMapper.selectOneById(postId);
            if (any == null) {
                throw new CustomException(404, "动态不存在");
            }
            return;
        }
        forceDeletePost(postId);
    }

    private void forceDeletePost(Long postId) {
        // 删 DB 前收集图片 object key，提交后清 MinIO；并清相关通知
        List<MomentsImage> images = imageMapper.selectListByQuery(
                QueryWrapper.create().eq("post_id", postId)
        );
        List<String> objectKeys = images.stream()
                .map(MomentsImage::getUrl)
                .filter(u -> u != null && !u.isBlank())
                .filter(u -> !mediaUrlService.isExternalHttpUrl(u))
                .toList();

        imageMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        likeMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        commentMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        // 清除与该动态相关的通知（like/comment/mention/at）
        notificationMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(MessageNotification::getRelatedId).eq(postId)
                        .and(MessageNotification::getType).like("moments_%")
        );
        postMapper.deleteById(postId);

        if (!objectKeys.isEmpty()) {
            for (String keyOrUrl : objectKeys) {
                try {
                    fileStorageService.deleteFile(keyOrUrl);
                } catch (Exception e) {
                    log.warn("删除朋友圈图片对象失败: key={}, err={}", keyOrUrl, e.getMessage());
                }
            }
        }
    }

    private void assertPostExists(Long postId) {
        if (postMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", postId)
                        .eq("deleted", 0)) == null) {
            throw new CustomException(404, "动态不存在");
        }
    }

    private static String escapeLike(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Set<Long> getFriendIds(Long userId) {
        List<SysUserRelation> relations = sysUserRelationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("status", 1)
                        .eq("deleted", 0)
        );
        return relations.stream()
                .map(SysUserRelation::getFriendId)
                .collect(Collectors.toSet());
    }

    private Map<Long, List<MomentsImage>> loadImages(List<MomentsPost> posts) {
        List<Long> postIds = posts.stream().map(MomentsPost::getId).collect(Collectors.toList());
        List<MomentsImage> images = imageMapper.selectListByQuery(
                QueryWrapper.create().in("post_id", postIds).orderBy("sort_order", true)
        );
        return images.stream().collect(Collectors.groupingBy(MomentsImage::getPostId));
    }

    private Map<Long, List<MomentsLike>> loadLikes(List<MomentsPost> posts) {
        List<Long> postIds = posts.stream().map(MomentsPost::getId).collect(Collectors.toList());
        List<MomentsLike> likes = likeMapper.selectListByQuery(
                QueryWrapper.create().in("post_id", postIds)
        );
        return likes.stream().collect(Collectors.groupingBy(MomentsLike::getPostId));
    }

    private Map<Long, List<MomentsComment>> loadComments(List<MomentsPost> posts) {
        List<Long> postIds = posts.stream().map(MomentsPost::getId).collect(Collectors.toList());
        List<MomentsComment> comments = commentRepository.selectListByQuery(
                QueryWrapper.create().in("post_id", postIds).orderBy("create_time", true)
        );
        return comments.stream().collect(Collectors.groupingBy(MomentsComment::getPostId));
    }

    private Map<Long, SysUser> loadUsers(List<MomentsPost> posts, Map<Long, List<MomentsComment>> commentsMap) {
        Set<Long> userIds = new HashSet<>();
        posts.forEach(p -> userIds.add(p.getUserId()));
        commentsMap.values().forEach(list -> list.forEach(c -> userIds.add(c.getUserId())));

        List<SysUser> users = userMapper.selectListByQuery(
                QueryWrapper.create().in("id", new ArrayList<>(userIds))
        );
        return users.stream().collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private MomentsPostVO toPostVO(MomentsPost post, SysUser user,
                                   List<MomentsImage> images,
                                   List<MomentsLike> likes,
                                   List<MomentsComment> comments,
                                   Long currentUserId) {
        // 库中存 object key 或外链；MinIO 签发预签名，外链走 HMAC 代理降低追踪面
        List<MomentsImage> sortedImages = images.stream()
                .sorted(Comparator.comparingInt(MomentsImage::getSortOrder))
                .collect(Collectors.toList());
        List<String> imageUrls = new ArrayList<>();
        List<Long> imageIds = new ArrayList<>();
        for (MomentsImage img : sortedImages) {
            String url = resolveMomentsImageUrl(img.getUrl());
            if (url == null || url.isBlank()) {
                continue;
            }
            imageUrls.add(url);
            imageIds.add(mediaUrlService.isExternalHttpUrl(img.getUrl()) ? null : img.getId());
        }

        boolean liked = likes.stream().anyMatch(l -> l.getUserId().equals(currentUserId));

        // 批量加载所有评论者、点赞者及父评论用户，消除 N+1 查询
        Set<Long> involvedUserIds = new HashSet<>();
        likes.forEach(l -> involvedUserIds.add(l.getUserId()));
        comments.forEach(c -> involvedUserIds.add(c.getUserId()));
        Set<Long> parentIds = comments.stream()
                .filter(c -> c.getParentId() != null)
                .map(MomentsComment::getParentId)
                .collect(Collectors.toSet());
        Map<Long, MomentsComment> parentCommentMap = parentIds.isEmpty() ? Collections.emptyMap() :
                commentRepository.selectListByQuery(
                        QueryWrapper.create().in("id", new ArrayList<>(parentIds))
                ).stream().collect(Collectors.toMap(MomentsComment::getId, c -> c));
        parentCommentMap.values().forEach(p -> involvedUserIds.add(p.getUserId()));

        Map<Long, SysUser> involvedUserMap = involvedUserIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectListByQuery(
                        QueryWrapper.create().in("id", new ArrayList<>(involvedUserIds))
                ).stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        List<String> likedBy = likes.stream()
                .map(l -> {
                    SysUser liker = involvedUserMap.get(l.getUserId());
                    return liker != null ? liker.getNickname() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<MomentsCommentVO> commentVOs = comments.stream()
                .map(c -> {
                    SysUser commenter = involvedUserMap.get(c.getUserId());
                    return toCommentVO(c, commenter, parseMentions(c.getMentions()), parentCommentMap, involvedUserMap);
                })
                .collect(Collectors.toList());

        String timeStr = formatTime(post.getCreateTime());
        List<Long> atUserIds = parseMentions(post.getAtUsers());
        List<String> atUserNames = resolveUserNicknames(atUserIds);

        return MomentsPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(user != null ? user.getNickname() : null)
                .avatar(user != null ? mediaUrlService.resolveUserAvatar(user.getId(), user.getAvatar()) : null)
                .content(post.getContent())
                .images(imageUrls)
                .imageIds(imageIds)
                .location(post.getLocation())
                .atUsers(post.getAtUsers())
                .atUserNames(atUserNames)
                .visibility(post.getVisibility())
                .time(timeStr)
                .likes(likes.size())
                .liked(liked)
                .likedBy(likedBy)
                .comments(commentVOs)
                .build();
    }

    /** 批量解析用户昵称，保持入参顺序 */
    private List<String> resolveUserNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 一次批量查询代替 N 次单条查询
        Map<Long, SysUser> userMap = userMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(new ArrayList<>(userIds))
        ).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        List<String> names = new ArrayList<>(userIds.size());
        for (Long id : userIds) {
            SysUser u = userMap.get(id);
            if (u != null && u.getNickname() != null && !u.getNickname().isBlank()) {
                names.add(u.getNickname());
            }
        }
        return names;
    }

    private MomentsCommentVO toCommentVO(MomentsComment comment, SysUser user) {
        return toCommentVO(comment, user, parseMentions(comment.getMentions()), Collections.emptyMap(), Collections.emptyMap());
    }

    private MomentsCommentVO toCommentVO(MomentsComment comment, SysUser user, List<Long> mentions) {
        return toCommentVO(comment, user, mentions, Collections.emptyMap(), Collections.emptyMap());
    }

    private MomentsCommentVO toCommentVO(MomentsComment comment, SysUser user, List<Long> mentions,
                                         Map<Long, MomentsComment> parentCache,
                                         Map<Long, SysUser> userCache) {
        String replyToNickname = null;
        if (comment.getParentId() != null) {
            MomentsComment parent = parentCache.isEmpty()
                    ? commentRepository.selectOneById(comment.getParentId())
                    : parentCache.get(comment.getParentId());
            if (parent != null) {
                SysUser parentUser = userCache.isEmpty()
                        ? userMapper.selectOneById(parent.getUserId())
                        : userCache.get(parent.getUserId());
                if (parentUser != null) {
                    replyToNickname = parentUser.getNickname();
                }
            }
        }
        return MomentsCommentVO.builder()
                .id(comment.getId())
                .userId(comment.getUserId())
                .nickname(user != null ? user.getNickname() : null)
                .avatar(user != null ? mediaUrlService.resolveUserAvatar(user.getId(), user.getAvatar()) : null)
                .content(comment.getContent())
                .time(formatTime(comment.getCreateTime()))
                .mentions(mentions)
                .parentId(comment.getParentId())
                .replyToNickname(replyToNickname)
                .build();
    }

    private List<Long> parseMentions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<Long>>() {}));
        } catch (Exception e) {
            log.warn("解析 mentions JSON 失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 将 mentions 列表序列化为 JSON 字符串。
     */
    private String toJsonString(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            log.warn("序列化 mentions 失败: {}", ids, e);
            return null;
        }
    }

    /**
     * 清洗 mentions:去重/去空/剔除自身与 null。
     */
    private List<Long> sanitizeMentions(List<Long> raw, Long selfUserId) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id == null) continue;
            if (id.equals(selfUserId)) continue;
            set.add(id);
        }
        return new ArrayList<>(set);
    }

    /**
     * 从评论内容兜底解析 @提及：仅匹配「好友中昵称唯一」的用户，避免重名误@。
     * 前端应优先传 mentions userId 列表；此方法仅作兜底。
     */
    private List<Long> parseMentionedUserIds(String content, Long selfUserId) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@([^\\s@]{1,32})").matcher(content);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> friendIds = getFriendIds(selfUserId);
        if (friendIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysUser> friends = userMapper.selectListByQuery(
                QueryWrapper.create().where(SysUser::getId).in(friendIds)
        );
        Map<String, List<SysUser>> byNickname = friends.stream()
                .filter(u -> u.getNickname() != null && !u.getNickname().isBlank())
                .collect(Collectors.groupingBy(SysUser::getNickname));

        List<Long> ids = new ArrayList<>();
        for (String name : names) {
            List<SysUser> matched = byNickname.getOrDefault(name, List.of());
            // 仅昵称唯一时采纳，避免同名误伤
            if (matched.size() == 1) {
                Long id = matched.get(0).getId();
                if (id != null && !id.equals(selfUserId)) {
                    ids.add(id);
                }
            }
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 截取用于通知内容预览(避免 500 长度限制溢出)。
     */
    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    /**
     * 提取动态文本预览。
     */
    private String extractPostPreview(String content) {
        return truncate(content, 100);
    }

    private String formatTime(java.util.Date date) {
        if (date == null) return "";
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIME_FORMATTER);
    }

    @Override
    public String uploadImage(Long userId, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "文件不能为空");
        }
        String contentType = file.getContentType();
        boolean ok = contentType != null
                && (contentType.startsWith("image/") || contentType.startsWith("video/"));
        if (!ok) {
            throw new CustomException(400, "只能上传图片或视频文件");
        }
        try {
            if (contentType.startsWith("image/")) {
                com.linkx.server.common.ImageUploadValidator.assertSupportedImage(file);
            }
            // 只返回 object key 入库；列表/详情时再签发预签名 URL。
            String key = fileStorageService.uploadFile(file);
            objectKeyOwnershipService.claim(userId, key);
            return key;
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(500, "媒体上传失败");
        }
    }

    @Override
    public FileStorageService.StoredObject openImageContent(Long userId, Long imageId) {
        MomentsImage image = imageMapper.selectOneById(imageId);
        if (image == null) {
            throw new CustomException(404, "图片不存在");
        }
        MomentsPost post = postRepository.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", image.getPostId())
                        .eq("deleted", 0)
        );
        if (post == null) {
            throw new CustomException(404, "动态不存在");
        }
        if (!canViewPost(post, userId)) {
            throw new CustomException(403, "无权查看该动态");
        }
        String stored = image.getUrl();
        if (stored == null || stored.isBlank()) {
            throw new CustomException(400, "无效的媒体引用");
        }
        if (mediaUrlService.isExternalHttpUrl(stored)) {
            throw new CustomException(400, "外链图片请使用原始地址");
        }
        String key = fileStorageService.extractObjectKey(stored);
        if (key == null || key.isBlank()) {
            throw new CustomException(400, "无效的媒体引用");
        }
        return fileStorageService.openObject(key);
    }

    @Override
    public String getImageFileName(Long userId, Long imageId) {
        MomentsImage image = imageMapper.selectOneById(imageId);
        if (image == null) {
            throw new CustomException(404, "图片不存在");
        }
        MomentsPost post = postRepository.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", image.getPostId())
                        .eq("deleted", 0)
        );
        if (post == null) {
            throw new CustomException(404, "动态不存在");
        }
        if (!canViewPost(post, userId)) {
            throw new CustomException(403, "无权查看该动态");
        }
        String key = fileStorageService.extractObjectKey(image.getUrl());
        if (key != null && key.contains("/")) {
            String name = key.substring(key.lastIndexOf('/') + 1);
            if (!name.isBlank()) {
                return name;
            }
        }
        return "image.jpg";
    }

    /**
     * 外链（非本系统 MinIO）经 SSRF 校验后原样入库；本系统 key/预签名须属主，并只存 object key。
     */
    private String normalizeAndAuthorizeMomentsMedia(Long userId, String raw) {
        String trimmed = raw.trim();
        if (mediaUrlService.isExternalHttpUrl(trimmed)) {
            SafeExternalUrl.parseAndValidate(trimmed);
            return trimmed;
        }
        String key = fileStorageService.extractObjectKey(trimmed);
        if (key == null || key.isBlank()
                || key.contains("..")
                || key.startsWith("/")
                || key.contains("://")) {
            throw new CustomException(400, "无效的媒体引用");
        }
        objectKeyOwnershipService.assertOwned(userId, key);
        return key;
    }

    private String resolveMomentsImageUrl(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (mediaUrlService.isExternalHttpUrl(stored.trim())) {
            try {
                return externalMediaProxyService.wrapExternalUrl(stored.trim());
            } catch (CustomException e) {
                log.warn("朋友圈外链无法代理: {}", e.getMessage());
                return null;
            }
        }
        return mediaUrlService.resolve(stored);
    }

    private record SensitiveHit(String text, String matchedWords, String failReason, String original) {
        boolean matched() {
            return matchedWords != null && !matchedWords.isBlank();
        }
    }

    /**
     * @param existingTargetId 已有目标 ID 时（如编辑动态）在拦截阶段即可入审；新建时传 null，落库后再入审
     */
    private SensitiveHit filterSensitiveOrThrow(Long userId, String content, String targetType, String existingTargetId) {
        if (content == null || content.isBlank()) {
            return new SensitiveHit("", null, null, content);
        }
        SensitiveWordService.FilterResult result = sensitiveWordService.filter(content);
        if (result.matchedWords().isEmpty()) {
            return new SensitiveHit(result.text(), null, null, content);
        }
        String failReason = result.blocked()
                ? "blocked"
                : (result.filtered() ? "filtered" : (result.alerted() ? "alert" : "matched"));
        String matchedWords = String.join(",", result.matchedWords());
        if (result.blocked()) {
            if (existingTargetId != null) {
                enqueueSensitiveReview(userId, targetType, existingTargetId, content, matchedWords, failReason);
            } else {
                // 尚未落库：唯一 targetId，保证每次拦截都能单独入审
                String tempId = userId + ":blocked:" + System.currentTimeMillis();
                enqueueSensitiveReview(
                        userId, targetType, tempId, content, matchedWords, failReason);
            }
            throw new CustomException(400, "内容包含违规敏感词，无法发布");
        }
        return new SensitiveHit(result.text(), matchedWords, failReason, content);
    }

    private void enqueueSensitiveAfterPersist(Long userId, String targetType, String targetId, SensitiveHit hit) {
        if (hit == null || !hit.matched() || "blocked".equals(hit.failReason())) {
            return;
        }
        enqueueSensitiveReview(userId, targetType, targetId, hit.original(), hit.matchedWords(), hit.failReason());
    }

    private void enqueueSensitiveReview(Long userId,
                                        String targetType,
                                        String targetId,
                                        String content,
                                        String matchedWords,
                                        String failReason) {
        AdminReviewService reviewService = adminReviewService.getIfAvailable();
        if (reviewService == null) {
            return;
        }
        try {
            reviewService.createFromSensitiveHit(
                    userId, targetType, targetId, null, content, matchedWords, failReason);
        } catch (Exception e) {
            log.warn("朋友圈敏感词入审失败: {}", e.getMessage());
        }
    }
}
