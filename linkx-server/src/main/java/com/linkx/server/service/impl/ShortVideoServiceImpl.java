package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.SearchTextSupport;
import com.linkx.server.controller.dto.CommentShortVideoDTO;
import com.linkx.server.controller.dto.PublishShortVideoDTO;
import com.linkx.server.controller.dto.UpdateShortVideoDTO;
import com.linkx.server.controller.vo.ShortVideoCommentVO;
import com.linkx.server.controller.vo.ShortVideoPostVO;
import com.linkx.server.entity.*;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.*;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.ShortVideoService;
import com.linkx.server.service.admin.AdminReviewService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortVideoServiceImpl implements ShortVideoService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ShortVideoPostMapper postMapper;
    private final ShortVideoLikeMapper likeMapper;
    private final ShortVideoCommentMapper commentMapper;
    private final ShortVideoFollowMapper followMapper;
    private final SysUserMapper userMapper;
    private final SysUserRelationMapper sysUserRelationMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;
    private final MessageNotificationService notificationService;
    private final ImMessagePushService imPushService;
    private final ObjectMapper objectMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ObjectProvider<AdminReviewService> adminReviewService;
    private final MessageContentCipher messageContentCipher;

    @Override
    @Transactional
    public ShortVideoPostVO publish(Long userId, PublishShortVideoDTO dto) {
        SysUser user = requireUser(userId);
        String rawDesc = dto.getDescription() != null ? dto.getDescription().trim() : "";
        SensitiveHit hit = filterSensitiveOrThrow(userId, rawDesc, SysReviewTask.TARGET_SHORT_VIDEO, null);
        String videoKey = authorizeObjectKey(userId, dto.getVideoKey());
        String coverKey = dto.getCoverKey() != null && !dto.getCoverKey().isBlank()
                ? authorizeObjectKey(userId, dto.getCoverKey())
                : null;
        Integer visibility = dto.getVisibility() != null ? dto.getVisibility() : 0;

        ShortVideoPost post = ShortVideoPost.builder()
                .userId(userId)
                .description(hit.text())
                .videoKey(videoKey)
                .coverKey(coverKey)
                .durationMs(dto.getDurationMs())
                .visibility(visibility)
                .playCount(0L)
                .build();
        preparePostForStorage(post);
        postMapper.insert(post);
        enqueueSensitiveAfterPersist(userId, SysReviewTask.TARGET_SHORT_VIDEO, String.valueOf(post.getId()), hit);
        return toPostVO(post, user, Collections.emptyList(), Collections.emptyList(), userId, false);
    }

    @Override
    @Transactional
    public ShortVideoPostVO update(Long userId, Long postId, UpdateShortVideoDTO dto) {
        ShortVideoPost post = requireOwnedPost(userId, postId);
        if (dto.getDescription() != null) {
            SensitiveHit hit = filterSensitiveOrThrow(
                    userId, dto.getDescription().trim(), SysReviewTask.TARGET_SHORT_VIDEO, String.valueOf(postId));
            post.setDescription(hit.text());
            enqueueSensitiveAfterPersist(userId, SysReviewTask.TARGET_SHORT_VIDEO, String.valueOf(postId), hit);
        }
        if (dto.getCoverKey() != null && !dto.getCoverKey().isBlank()) {
            post.setCoverKey(authorizeObjectKey(userId, dto.getCoverKey()));
        }
        if (dto.getVisibility() != null) {
            post.setVisibility(dto.getVisibility());
        }
        preparePostForStorage(post);
        postMapper.update(post);
        SysUser user = requireUser(userId);
        return toPostVO(post, user, loadLikes(postId), loadComments(postId), userId, false);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long postId) {
        ShortVideoPost post = postMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", postId).eq("deleted", 0));
        if (post == null) {
            throw new CustomException(404, "作品不存在");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new CustomException(403, "只能删除自己的作品");
        }
        postMapper.deleteById(postId);
        likeMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        commentMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
    }

    @Override
    public List<ShortVideoPostVO> listDiscover(Long userId, Long beforeId, Integer limit, String q) {
        int pageSize = normalizeLimit(limit);

        QueryWrapper qw = QueryWrapper.create()
                .eq("deleted", 0)
                .and("(IFNULL(visibility, 0) <> 2 OR user_id = ?)", userId)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        applySearch(qw, q);
        return buildPostList(qw, userId);
    }

    @Override
    public List<ShortVideoPostVO> listFriends(Long userId, Long beforeId, Integer limit) {
        Set<Long> friendIds = getFriendIds(userId);
        friendIds.add(userId);
        int pageSize = normalizeLimit(limit);

        QueryWrapper qw = QueryWrapper.create()
                .in("user_id", new ArrayList<>(friendIds))
                .eq("deleted", 0)
                .and("(IFNULL(visibility, 0) <> 2 OR user_id = ?)", userId)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        return buildPostList(qw, userId);
    }

    @Override
    public List<ShortVideoPostVO> listFollowing(Long userId, Long beforeId, Integer limit) {
        List<ShortVideoFollow> follows = followMapper.selectListByQuery(
                QueryWrapper.create().eq("follower_id", userId).eq("deleted", 0));
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> followeeIds = follows.stream().map(ShortVideoFollow::getFolloweeId).collect(Collectors.toSet());
        int pageSize = normalizeLimit(limit);

        QueryWrapper qw = QueryWrapper.create()
                .in("user_id", new ArrayList<>(followeeIds))
                .eq("deleted", 0)
                .and("(IFNULL(visibility, 0) <> 2 OR user_id = ?)", userId)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        return buildPostList(qw, userId);
    }

    @Override
    public List<ShortVideoPostVO> listByUser(Long userId, Long targetUserId, Long beforeId, Integer limit) {
        boolean self = Objects.equals(userId, targetUserId);
        boolean friend = self || getFriendIds(userId).contains(targetUserId);
        int pageSize = normalizeLimit(limit);

        QueryWrapper qw = QueryWrapper.create()
                .eq("user_id", targetUserId)
                .eq("deleted", 0);
        if (!self) {
            if (friend) {
                qw.and("IFNULL(visibility, 0) <> 2");
            } else {
                qw.and("IFNULL(visibility, 0) = 0");
            }
        }
        qw.orderBy("create_time", false).orderBy("id", false).limit(pageSize);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        return buildPostList(qw, userId);
    }

    @Override
    @Transactional
    public void like(Long userId, Long postId) {
        ShortVideoPost post = assertCanInteract(userId, postId);
        ShortVideoLike existing = likeMapper.selectOneByQuery(
                QueryWrapper.create().eq("post_id", postId).eq("user_id", userId));
        if (existing != null) {
            return;
        }
        LogicDeleteManager.execWithoutLogicDelete(() ->
                likeMapper.deleteByQuery(
                        QueryWrapper.create().eq("post_id", postId).eq("user_id", userId)));
        try {
            likeMapper.insert(ShortVideoLike.builder().postId(postId).userId(userId).build());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return;
        }
        if (!post.getUserId().equals(userId)) {
            notifyAuthor(post.getUserId(), userId, "short_video_like", postId, preview(post.getDescription()));
        }
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        likeMapper.deleteByQuery(
                QueryWrapper.create().eq("post_id", postId).eq("user_id", userId));
    }

    @Override
    @Transactional
    public ShortVideoCommentVO comment(Long userId, Long postId, CommentShortVideoDTO dto) {
        ShortVideoPost post = assertCanInteract(userId, postId);
        SysUser user = requireUser(userId);
        if (dto.getParentId() != null) {
            ShortVideoComment parent = commentMapper.selectOneByQuery(
                    QueryWrapper.create().eq("id", dto.getParentId()).eq("deleted", 0));
            if (parent == null) {
                throw new CustomException(404, "引用的父评论不存在");
            }
            if (!postId.equals(parent.getPostId())) {
                throw new CustomException(403, "引用的父评论不属于该作品");
            }
        }
        String raw = dto.getContent() == null ? "" : dto.getContent().trim();
        SensitiveHit hit = filterSensitiveOrThrow(userId, raw, SysReviewTask.TARGET_SHORT_VIDEO_COMMENT, null);
        List<Long> mentions = sanitizeMentions(dto.getMentions(), userId);
        String mentionJson = mentions.isEmpty() ? null : toJson(mentions);

        ShortVideoComment comment = ShortVideoComment.builder()
                .postId(postId)
                .userId(userId)
                .content(hit.text())
                .parentId(dto.getParentId())
                .mentions(mentionJson)
                .build();
        prepareCommentForStorage(comment);
        commentMapper.insert(comment);
        enqueueSensitiveAfterPersist(
                userId, SysReviewTask.TARGET_SHORT_VIDEO_COMMENT, String.valueOf(comment.getId()), hit);

        if (!post.getUserId().equals(userId)) {
            notifyAuthor(post.getUserId(), userId, "short_video_comment", postId, truncate(raw, 100));
        }
        return toCommentVO(comment, user, null);
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        ShortVideoComment comment = commentMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", commentId).eq("deleted", 0));
        if (comment == null) {
            throw new CustomException(404, "评论不存在");
        }
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new CustomException(403, "只能删除自己的评论");
        }
        commentMapper.deleteById(commentId);
    }

    @Override
    @Transactional
    public void follow(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new CustomException(400, "不能关注自己");
        }
        if (userMapper.selectOneById(targetUserId) == null) {
            throw new CustomException(404, "用户不存在");
        }
        ShortVideoFollow existing = followMapper.selectOneByQuery(
                QueryWrapper.create().eq("follower_id", userId).eq("followee_id", targetUserId));
        if (existing != null) {
            return;
        }
        LogicDeleteManager.execWithoutLogicDelete(() ->
                followMapper.deleteByQuery(
                        QueryWrapper.create().eq("follower_id", userId).eq("followee_id", targetUserId)));
        try {
            followMapper.insert(ShortVideoFollow.builder()
                    .followerId(userId)
                    .followeeId(targetUserId)
                    .build());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            /* idempotent */
        }
    }

    @Override
    @Transactional
    public void unfollow(Long userId, Long targetUserId) {
        followMapper.deleteByQuery(
                QueryWrapper.create().eq("follower_id", userId).eq("followee_id", targetUserId));
    }

    @Override
    @Transactional
    public void recordPlay(Long userId, Long postId) {
        assertCanView(userId, postId);
        ShortVideoPost current = postMapper.selectOneById(postId);
        long next = (current != null && current.getPlayCount() != null ? current.getPlayCount() : 0L) + 1L;
        UpdateChain.of(ShortVideoPost.class)
                .set(ShortVideoPost::getPlayCount, next)
                .where(ShortVideoPost::getId).eq(postId)
                .update();
    }

    @Override
    public String uploadMedia(Long userId, MultipartFile file) {
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
            String key = fileStorageService.uploadFile(file);
            objectKeyOwnershipService.claim(userId, key);
            objectKeyOwnershipService.assertOwned(userId, key);
            return key;
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(500, "媒体上传失败");
        }
    }

    @Override
    public FileStorageService.StoredObject openVideoContent(Long userId, Long postId) {
        ShortVideoPost post = assertCanView(userId, postId);
        return fileStorageService.openObject(post.getVideoKey());
    }

    @Override
    public FileStorageService.StoredObject openCoverContent(Long userId, Long postId) {
        ShortVideoPost post = assertCanView(userId, postId);
        if (post.getCoverKey() == null || post.getCoverKey().isBlank()) {
            throw new CustomException(404, "封面不存在");
        }
        return fileStorageService.openObject(post.getCoverKey());
    }

    @Override
    @Transactional
    public void adminDeletePost(Long postId) {
        postMapper.deleteById(postId);
        likeMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        commentMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
    }

    @Override
    @Transactional
    public void adminDeleteComment(Long commentId) {
        commentMapper.deleteById(commentId);
    }

    private List<ShortVideoPostVO> buildPostList(QueryWrapper qw, Long viewerId) {
        List<ShortVideoPost> posts = postMapper.selectListByQuery(qw);
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        decryptPosts(posts);
        Set<Long> postIds = posts.stream().map(ShortVideoPost::getId).collect(Collectors.toSet());
        Map<Long, List<ShortVideoLike>> likesMap = loadLikesMap(postIds);
        Map<Long, List<ShortVideoComment>> commentsMap = loadCommentsMap(postIds);
        Set<Long> authorIds = posts.stream().map(ShortVideoPost::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(authorIds);
        Set<Long> following = loadFollowingSet(viewerId, authorIds);

        List<ShortVideoPostVO> result = new ArrayList<>();
        for (ShortVideoPost post : posts) {
            if (!canViewPost(post, viewerId)) {
                continue;
            }
            SysUser author = users.get(post.getUserId());
            boolean followingAuthor = following.contains(post.getUserId());
            result.add(toPostVO(
                    post,
                    author,
                    likesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    commentsMap.getOrDefault(post.getId(), Collections.emptyList()),
                    viewerId,
                    followingAuthor));
        }
        return result;
    }

    private ShortVideoPostVO toPostVO(
            ShortVideoPost post,
            SysUser author,
            List<ShortVideoLike> likes,
            List<ShortVideoComment> comments,
            Long viewerId,
            boolean followingAuthor) {
        decryptPost(post);
        boolean liked = likes.stream().anyMatch(l -> Objects.equals(l.getUserId(), viewerId));
        List<ShortVideoCommentVO> commentVOs = buildCommentVOs(comments);
        return ShortVideoPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(author != null ? author.getNickname() : null)
                .avatar(author != null ? mediaUrlService.resolveUserAvatar(author.getId(), author.getAvatar()) : null)
                .description(post.getDescription())
                .videoUrl(mediaUrlService.resolve(post.getVideoKey()))
                .coverUrl(post.getCoverKey() != null ? mediaUrlService.resolve(post.getCoverKey()) : null)
                .durationMs(post.getDurationMs())
                .visibility(post.getVisibility())
                .playCount(post.getPlayCount() != null ? post.getPlayCount() : 0L)
                .time(formatTime(post.getCreateTime()))
                .likes(likes.size())
                .liked(liked)
                .followingAuthor(followingAuthor)
                .comments(commentVOs)
                .build();
    }

    private List<ShortVideoCommentVO> buildCommentVOs(List<ShortVideoComment> comments) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        decryptComments(comments);
        Set<Long> userIds = comments.stream().map(ShortVideoComment::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(userIds);
        Map<Long, ShortVideoComment> byId = comments.stream()
                .collect(Collectors.toMap(ShortVideoComment::getId, c -> c, (a, b) -> a));
        List<ShortVideoCommentVO> vos = new ArrayList<>();
        for (ShortVideoComment comment : comments) {
            SysUser user = users.get(comment.getUserId());
            String replyToNickname = null;
            if (comment.getParentId() != null) {
                ShortVideoComment parent = byId.get(comment.getParentId());
                if (parent != null) {
                    SysUser parentUser = users.get(parent.getUserId());
                    replyToNickname = parentUser != null ? parentUser.getNickname() : null;
                }
            }
            vos.add(toCommentVO(comment, user, replyToNickname));
        }
        return vos;
    }

    private ShortVideoCommentVO toCommentVO(ShortVideoComment comment, SysUser user, String replyToNickname) {
        decryptComment(comment);
        return ShortVideoCommentVO.builder()
                .id(comment.getId())
                .userId(comment.getUserId())
                .nickname(user != null ? user.getNickname() : null)
                .avatar(user != null ? mediaUrlService.resolveUserAvatar(user.getId(), user.getAvatar()) : null)
                .content(comment.getContent())
                .time(formatTime(comment.getCreateTime()))
                .mentions(parseMentions(comment.getMentions()))
                .parentId(comment.getParentId())
                .replyToNickname(replyToNickname)
                .build();
    }

    private ShortVideoPost requireOwnedPost(Long userId, Long postId) {
        ShortVideoPost post = postMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", postId).eq("deleted", 0));
        if (post == null) {
            throw new CustomException(404, "作品不存在");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new CustomException(403, "只能编辑自己的作品");
        }
        decryptPost(post);
        return post;
    }

    private ShortVideoPost assertCanInteract(Long userId, Long postId) {
        return assertCanView(userId, postId);
    }

    private ShortVideoPost assertCanView(Long userId, Long postId) {
        ShortVideoPost post = postMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", postId).eq("deleted", 0));
        if (post == null) {
            throw new CustomException(404, "作品不存在");
        }
        decryptPost(post);
        if (!canViewPost(post, userId)) {
            throw new CustomException(403, "无权查看该作品");
        }
        return post;
    }

    private boolean canViewPost(ShortVideoPost post, Long viewerId) {
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

    private void preparePostForStorage(ShortVideoPost post) {
        String plainDesc = post.getDescription();
        if (plainDesc == null) {
            plainDesc = "";
            post.setDescription(plainDesc);
        }
        post.setSearchText(SearchTextSupport.buildMomentsSearchText(plainDesc, null));
        if (messageContentCipher.isEnabled() && !plainDesc.isBlank()) {
            post.setDescription(messageContentCipher.encryptPlaintextForStorage(plainDesc));
            post.setDescriptionEncVersion(MessageContentCipher.ENC_VERSION);
        } else {
            post.setDescriptionEncVersion((byte) 0);
        }
    }

    private void prepareCommentForStorage(ShortVideoComment comment) {
        if (messageContentCipher.isEnabled() && comment.getContent() != null && !comment.getContent().isBlank()) {
            comment.setContent(messageContentCipher.encryptPlaintextForStorage(comment.getContent()));
            comment.setContentEncVersion(MessageContentCipher.ENC_VERSION);
        } else {
            comment.setContentEncVersion((byte) 0);
        }
    }

    private void decryptPost(ShortVideoPost post) {
        if (post == null) {
            return;
        }
        if (messageContentCipher.isEncryptedContent(post.getDescription(), post.getDescriptionEncVersion())) {
            post.setDescription(messageContentCipher.decryptTextFromStorage(
                    post.getDescription(), post.getDescriptionEncVersion()));
        }
    }

    private void decryptPosts(List<ShortVideoPost> posts) {
        for (ShortVideoPost post : posts) {
            decryptPost(post);
        }
    }

    private void decryptComments(List<ShortVideoComment> comments) {
        for (ShortVideoComment comment : comments) {
            decryptComment(comment);
        }
    }

    private void decryptComment(ShortVideoComment comment) {
        if (comment == null) {
            return;
        }
        if (messageContentCipher.isEncryptedContent(comment.getContent(), comment.getContentEncVersion())) {
            comment.setContent(messageContentCipher.decryptTextFromStorage(
                    comment.getContent(), comment.getContentEncVersion()));
        }
    }

    private String authorizeObjectKey(Long userId, String raw) {
        String key = fileStorageService.extractObjectKey(raw.trim());
        if (key == null || key.isBlank() || key.contains("..") || key.startsWith("/") || key.contains("://")) {
            throw new CustomException(400, "无效的媒体引用");
        }
        objectKeyOwnershipService.assertOwned(userId, key);
        return key;
    }

    private Set<Long> getFriendIds(Long userId) {
        List<SysUserRelation> relations = sysUserRelationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .eq("status", 1)
                        .eq("deleted", 0));
        Set<Long> ids = new HashSet<>();
        for (SysUserRelation rel : relations) {
            if (rel.getFriendId() != null) {
                ids.add(rel.getFriendId());
            }
        }
        return ids;
    }

    private Set<Long> loadFollowingSet(Long viewerId, Set<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<ShortVideoFollow> rows = followMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("follower_id", viewerId)
                        .in("followee_id", new ArrayList<>(authorIds))
                        .eq("deleted", 0));
        return rows.stream().map(ShortVideoFollow::getFolloweeId).collect(Collectors.toSet());
    }

    private Map<Long, SysUser> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = userMapper.selectListByQuery(
                QueryWrapper.create().in("id", new ArrayList<>(userIds)));
        return users.stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, List<ShortVideoLike>> loadLikesMap(Set<Long> postIds) {
        List<ShortVideoLike> likes = likeMapper.selectListByQuery(
                QueryWrapper.create().in("post_id", new ArrayList<>(postIds)).eq("deleted", 0));
        return likes.stream().collect(Collectors.groupingBy(ShortVideoLike::getPostId));
    }

    private Map<Long, List<ShortVideoComment>> loadCommentsMap(Set<Long> postIds) {
        List<ShortVideoComment> comments = commentMapper.selectListByQuery(
                QueryWrapper.create()
                        .in("post_id", new ArrayList<>(postIds))
                        .eq("deleted", 0)
                        .orderBy("create_time", true));
        return comments.stream().collect(Collectors.groupingBy(ShortVideoComment::getPostId));
    }

    private List<ShortVideoLike> loadLikes(Long postId) {
        return likeMapper.selectListByQuery(
                QueryWrapper.create().eq("post_id", postId).eq("deleted", 0));
    }

    private List<ShortVideoComment> loadComments(Long postId) {
        return commentMapper.selectListByQuery(
                QueryWrapper.create().eq("post_id", postId).eq("deleted", 0).orderBy("create_time", true));
    }

    private void applySearch(QueryWrapper qw, String q) {
        if (q == null || q.isBlank()) {
            return;
        }
        String keyword = q.trim();
        if (messageContentCipher.isEnabled()) {
            qw.and("search_text LIKE ?", "%" + keyword + "%");
        } else {
            qw.and("description LIKE ?", "%" + keyword + "%");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 50);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        return user;
    }

    private void notifyAuthor(Long authorId, Long actorId, String type, Long postId, String previewText) {
        try {
            SysUser actor = userMapper.selectOneById(actorId);
            notificationService.create(
                    authorId,
                    actorId,
                    actor != null ? actor.getNickname() : null,
                    actor != null ? actor.getAvatar() : null,
                    type,
                    postId,
                    previewText);
            imPushService.pushToUser(authorId, "notification_refresh", Map.of("type", type));
        } catch (Exception e) {
            log.warn("短视频通知失败 type={} postId={}: {}", type, postId, e.getMessage());
        }
    }

    private String preview(String description) {
        return truncate(description != null ? description : "", 100);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String formatTime(java.util.Date date) {
        if (date == null) {
            return "";
        }
        return TIME_FORMATTER.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }

    private List<Long> parseMentions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Long> sanitizeMentions(List<Long> raw, Long selfUserId) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id == null || id.equals(selfUserId)) {
                continue;
            }
            set.add(id);
        }
        return new ArrayList<>(set);
    }

    private record SensitiveHit(String text, String matchedWords, String failReason, String original) {
        boolean matched() {
            return matchedWords != null && !matchedWords.isBlank();
        }
    }

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
                enqueueSensitiveReview(userId, targetType, "pending:" + UUID.randomUUID(), content, matchedWords, failReason);
            }
            throw new CustomException(400, "内容包含违规词，请修改后重试");
        }
        return new SensitiveHit(result.text(), matchedWords, failReason, content);
    }

    private void enqueueSensitiveAfterPersist(Long userId, String targetType, String targetId, SensitiveHit hit) {
        if (hit == null || !hit.matched() || "blocked".equals(hit.failReason())) {
            return;
        }
        enqueueSensitiveReview(userId, targetType, targetId, hit.original(), hit.matchedWords(), hit.failReason());
    }

    private void enqueueSensitiveReview(
            Long userId, String targetType, String targetId, String content, String matchedWords, String failReason) {
        AdminReviewService review = adminReviewService.getIfAvailable();
        if (review == null) {
            return;
        }
        try {
            review.createFromSensitiveHit(userId, targetType, targetId, null, content, matchedWords, failReason);
        } catch (Exception e) {
            log.warn("短视频敏感词入审失败 target={}: {}", targetId, e.getMessage());
        }
    }
}
