package com.linkx.server.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;
import com.linkx.server.entity.*;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.*;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.MomentsService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final MomentsImageMapper imageMapper;
    private final MomentsLikeMapper likeMapper;
    private final MomentsCommentMapper commentMapper;
    private final SysUserMapper userMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final MessageNotificationService notificationService;
    private final ImMessagePushService imPushService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public MomentsPostVO publish(Long userId, PublishMomentsDTO dto) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

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
                .content(dto.getContent())
                .location(dto.getLocation())
                .atUsers(atUsersJson)
                .visibility(visibility)
                .build();
        postMapper.insert(post);

        List<MomentsImage> savedImages = new ArrayList<>();
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            int order = 0;
            for (String imageUrl : dto.getImages()) {
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }
                MomentsImage image = MomentsImage.builder()
                        .postId(post.getId())
                        .url(imageUrl.trim())
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

        return toPostVO(post, user, savedImages, Collections.emptyList(), Collections.emptyList(), userId);
    }

    @Override
    public List<MomentsPostVO> list(Long userId) {
        Set<Long> friendIds = getFriendIds(userId);
        friendIds.add(userId);

        List<MomentsPost> posts = postMapper.selectListByQuery(
                QueryWrapper.create()
                        .in("user_id", new ArrayList<>(friendIds))
                        .eq("deleted", 0)
                        .orderBy("create_time", false)
                        .limit(50)
        );

        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<MomentsImage>> imagesMap = loadImages(posts);
        Map<Long, List<MomentsLike>> likesMap = loadLikes(posts);
        Map<Long, List<MomentsComment>> commentsMap = loadComments(posts);
        Map<Long, SysUser> userMap = loadUsers(posts, commentsMap);

        List<MomentsPostVO> result = new ArrayList<>();
        for (MomentsPost post : posts) {
            if (!canViewPost(post, userId)) {
                continue;
            }
            result.add(toPostVO(post,
                    userMap.get(post.getUserId()),
                    imagesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    likesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    commentsMap.getOrDefault(post.getId(), Collections.emptyList()),
                    userId));
        }
        return result;
    }

    @Override
    public List<MomentsPostVO> listByUser(Long userId, Long targetUserId) {
        List<MomentsPost> posts = postMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", targetUserId)
                        .eq("deleted", 0)
                        .orderBy("create_time", false)
                        .limit(50)
        );

        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<MomentsImage>> imagesMap = loadImages(posts);
        Map<Long, List<MomentsLike>> likesMap = loadLikes(posts);
        Map<Long, List<MomentsComment>> commentsMap = loadComments(posts);
        SysUser targetUser = userMapper.selectOneById(targetUserId);

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

    /**
     * 可见性校验：0=公开，1=仅好友（列表已按好友圈过滤），2=私密仅作者可见
     */
    private boolean canViewPost(MomentsPost post, Long viewerId) {
        Integer visibility = post.getVisibility();
        if (visibility == null || visibility == 0 || visibility == 1) {
            return true;
        }
        if (visibility == 2) {
            return Objects.equals(post.getUserId(), viewerId);
        }
        return true;
    }

    @Override
    @Transactional
    public void like(Long userId, Long postId) {
        assertPostExists(postId);

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

        likeMapper.insert(MomentsLike.builder()
                .postId(postId)
                .userId(userId)
                .build());

        // 给动态作者推送消息通知(不通知自己赞自己)
        MomentsPost post = postMapper.selectOneById(postId);
        if (post != null && !post.getUserId().equals(userId)) {
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
        assertPostExists(postId);

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

        MomentsComment comment = MomentsComment.builder()
                .postId(postId)
                .userId(userId)
                .content(dto.getContent())
                .parentId(dto.getParentId())
                .mentions(mentionJson)
                .build();
        commentMapper.insert(comment);

        // 给动态作者推送消息通知
        MomentsPost post = postMapper.selectOneById(postId);
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
        MomentsComment comment = commentMapper.selectOneById(commentId);
        if (comment == null) {
            throw new CustomException(404, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new CustomException(403, "无权删除此评论");
        }
        commentMapper.deleteById(commentId);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long postId) {
        MomentsPost post = postMapper.selectOneByQuery(
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

        imageMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        likeMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        commentMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        postMapper.deleteById(postId);
    }

    private void assertPostExists(Long postId) {
        if (postMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("id", postId)
                        .eq("deleted", 0)) == null) {
            throw new CustomException(404, "动态不存在");
        }
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
        List<MomentsComment> comments = commentMapper.selectListByQuery(
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
        // 库中存 object key（或旧版完整 URL），对外统一签发可访问的预签名 URL
        List<String> imageUrls = images.stream()
                .sorted(Comparator.comparingInt(MomentsImage::getSortOrder))
                .map(img -> mediaUrlService.resolve(img.getUrl()))
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .collect(Collectors.toList());

        boolean liked = likes.stream().anyMatch(l -> l.getUserId().equals(currentUserId));
        List<String> likedBy = likes.stream()
                .map(l -> {
                    SysUser liker = userMapper.selectOneById(l.getUserId());
                    return liker != null ? liker.getNickname() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<MomentsCommentVO> commentVOs = comments.stream()
                .map(c -> {
                    SysUser commenter = userMapper.selectOneById(c.getUserId());
                    return toCommentVO(c, commenter, parseMentions(c.getMentions()));
                })
                .collect(Collectors.toList());

        String timeStr = formatTime(post.getCreateTime());
        List<Long> atUserIds = parseMentions(post.getAtUsers());
        List<String> atUserNames = resolveUserNicknames(atUserIds);

        return MomentsPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(user != null ? user.getNickname() : null)
                .avatar(user != null ? mediaUrlService.resolve(user.getAvatar()) : null)
                .content(post.getContent())
                .images(imageUrls)
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
        List<String> names = new ArrayList<>(userIds.size());
        for (Long id : userIds) {
            SysUser u = userMapper.selectOneById(id);
            if (u != null && u.getNickname() != null && !u.getNickname().isBlank()) {
                names.add(u.getNickname());
            }
        }
        return names;
    }

    private MomentsCommentVO toCommentVO(MomentsComment comment, SysUser user) {
        return toCommentVO(comment, user, parseMentions(comment.getMentions()));
    }

    private MomentsCommentVO toCommentVO(MomentsComment comment, SysUser user, List<Long> mentions) {
        return MomentsCommentVO.builder()
                .id(comment.getId())
                .userId(comment.getUserId())
                .nickname(user != null ? user.getNickname() : null)
                .avatar(user != null ? mediaUrlService.resolve(user.getAvatar()) : null)
                .content(comment.getContent())
                .time(formatTime(comment.getCreateTime()))
                .mentions(mentions)
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
     * 从评论内容兜底解析 @提及:扫描 @昵称 形式,匹配好友/全部用户中匹配昵称的 ID。
     * 仅作为服务端兜底,前端通常会在提交前就传入 mentions 列表。
     */
    private List<Long> parseMentionedUserIds(String content, Long selfUserId) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@([^\\s@]{1,32})").matcher(content);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty()) names.add(name);
        }
        if (names.isEmpty()) return Collections.emptyList();

        List<SysUser> users = userMapper.selectListByQuery(
                QueryWrapper.create().in("nickname", names)
        );
        List<Long> ids = new ArrayList<>();
        for (SysUser u : users) {
            if (u != null && u.getId() != null && !u.getId().equals(selfUserId)) {
                ids.add(u.getId());
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
    public String uploadImage(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "图片不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(400, "只能上传图片文件");
        }
        try {
            // 只返回 object key 入库；列表/详情时再签发预签名 URL。
            // 若返回完整签名 URL，长度常超过 moments_image.url(varchar 500) 导致发布失败。
            return fileStorageService.uploadFile(file);
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(500, "图片上传失败");
        }
    }
}
