package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.common.SearchTextSupport;
import com.linkx.server.common.ShortVideoHashtagSupport;
import com.linkx.server.common.ShortVideoTopicHotScore;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.controller.dto.CommentShortVideoDTO;
import com.linkx.server.controller.dto.PublishShortVideoDTO;
import com.linkx.server.controller.dto.ReportShortVideoDTO;
import com.linkx.server.controller.dto.UpdateShortVideoDTO;
import com.linkx.server.controller.vo.ShortVideoAuthorVO;
import com.linkx.server.controller.vo.ShortVideoBlockedUserVO;
import com.linkx.server.controller.vo.ShortVideoCommentVO;
import com.linkx.server.controller.vo.ShortVideoFollowingUserVO;
import com.linkx.server.controller.vo.ShortVideoFollowerUserVO;
import com.linkx.server.controller.vo.ShortVideoPostVO;
import com.linkx.server.controller.vo.ShortVideoTopicVO;
import com.linkx.server.entity.*;
import com.linkx.server.entity.admin.SysReviewTask;
import com.linkx.server.exception.CustomException;
import com.linkx.server.im.ImMessagePushService;
import com.linkx.server.mapper.*;
import com.linkx.server.mapper.row.ShortVideoCommentCountRow;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.MessageNotificationService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.linkx.server.service.SensitiveWordService;
import com.linkx.server.service.ShortVideoService;
import com.linkx.server.storage.ObjectStorageRouter;
import com.linkx.server.service.admin.AdminReviewService;
import com.linkx.server.task.ShortVideoTranscodeAsyncTrigger;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortVideoServiceImpl implements ShortVideoService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String PLAY_DEDUP_KEY_PREFIX = "sv:play:";
    private static final String SHARE_DEDUP_KEY_PREFIX = "sv:share:";
    private static final Duration PLAY_DEDUP_TTL = Duration.ofHours(24);
    private static final Duration SHARE_DEDUP_TTL = Duration.ofHours(24);
    private static final int COMMENT_PAGE_SIZE = 20;
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    private static final int MAX_DURATION_MS = 60_000;
    private static final int DISCOVER_CANDIDATE_MULTIPLIER = 5;
    private static final int DISCOVER_MAX_CANDIDATES = 100;
    private static final double DISCOVER_FOLLOWING_BOOST = 1.5D;
    private static final double DISCOVER_FRIEND_BOOST = 1.2D;
    private static final double DISCOVER_FRESH_BOOST = 1.25D;
    private static final double DISCOVER_SEEN_PENALTY = 0.12D;
    private static final int DISCOVER_FRESH_HOURS = 6;
    private static final int HOT_TOPIC_DEFAULT_LIMIT = 10;
    private static final int HOT_TOPIC_MAX_LIMIT = 30;
    private static final int HOT_VIDEO_DEFAULT_LIMIT = 10;
    private static final int HOT_VIDEO_MAX_LIMIT = 30;
    private static final int HOT_VIDEO_CANDIDATE_MULTIPLIER = 8;
    private static final int HOT_VIDEO_MAX_CANDIDATES = 120;
    private static final PostInteractionStats EMPTY_INTERACTION = new PostInteractionStats(0, 0, false, false);

    private record PostInteractionStats(int likeCount, int favoriteCount, boolean liked, boolean favorited) {
    }

    private final ShortVideoPostMapper postMapper;
    private final ShortVideoLikeMapper likeMapper;
    private final ShortVideoFavoriteMapper favoriteMapper;
    private final ShortVideoCommentMapper commentMapper;
    private final ShortVideoCommentLikeMapper commentLikeMapper;
    private final ShortVideoCommentSqlMapper commentSqlMapper;
    private final ShortVideoInteractionSqlMapper interactionSqlMapper;
    private final ShortVideoFollowMapper followMapper;
    private final ShortVideoNotInterestedMapper notInterestedMapper;
    private final ShortVideoAuthorBlockMapper authorBlockMapper;
    private final ShortVideoTopicMapper topicMapper;
    private final ShortVideoPostTopicMapper postTopicMapper;
    private final ShortVideoTopicSqlMapper topicSqlMapper;
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
    private final StringRedisTemplate redisTemplate;
    private final ObjectStorageRouter objectStorageRouter;
    private final LinkxProperties linkxProperties;
    private final ShortVideoTranscodeAsyncTrigger transcodeAsyncTrigger;

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
        if (dto.getDurationMs() != null && dto.getDurationMs() > MAX_DURATION_MS) {
            throw new CustomException(400, "视频时长不能超过60秒");
        }
        Integer visibility = dto.getVisibility() != null ? dto.getVisibility() : 0;

        ShortVideoPost post = ShortVideoPost.builder()
                .userId(userId)
                .description(hit.text())
                .videoKey(videoKey)
                .storageProvider(objectStorageRouter.activeProvider().toWire())
                .coverKey(coverKey)
                .durationMs(dto.getDurationMs())
                .visibility(visibility)
                .playCount(0L)
                .shareCount(0L)
                .transcodeStatus(resolveInitialTranscodeStatus())
                .build();
        preparePostForStorage(post);
        postMapper.insert(post);
        syncPostTopics(post.getId(), hit.text());
        enqueueSensitiveAfterPersist(userId, SysReviewTask.TARGET_SHORT_VIDEO, String.valueOf(post.getId()), hit);
        if ("pending".equals(post.getTranscodeStatus())) {
            scheduleTranscodeAfterCommit(post.getId());
        }
        return toPostVO(post, user, EMPTY_INTERACTION, Collections.emptyList(), userId, false, 0,
                ShortVideoHashtagSupport.extract(hit.text()));
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
            syncPostTopics(postId, hit.text());
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
        int commentCount = countComments(postId);
        List<ShortVideoComment> comments = loadCommentPage(postId, null, COMMENT_PAGE_SIZE);
        PostInteractionStats interaction = loadInteractionStats(postId, userId);
        return toPostVO(post, user, interaction, comments, userId, false, commentCount,
                loadTopicsForPost(postId));
    }

    @Override
    public ShortVideoPostVO getPost(Long viewerId, Long postId) {
        ShortVideoPost post = assertCanView(viewerId, postId);
        SysUser author = requireUser(post.getUserId());
        PostInteractionStats interaction = loadInteractionStats(postId, viewerId);
        int commentCount = countComments(postId);
        List<ShortVideoComment> comments = loadCommentPage(postId, null, COMMENT_PAGE_SIZE);
        boolean followingAuthor = loadFollowingSet(viewerId, Set.of(post.getUserId()))
                .contains(post.getUserId());
        return toPostVO(post, author, interaction, comments, viewerId, followingAuthor, commentCount,
                loadTopicsForPost(postId));
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
        favoriteMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        commentMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
    }

    @Override
    public List<ShortVideoPostVO> listDiscover(Long userId, Long beforeId, Integer limit, String q) {
        int pageSize = normalizeLimit(limit);
        if (q != null && !q.isBlank()) {
            QueryWrapper qw = QueryWrapper.create()
                    .eq("deleted", 0)
                    .and("(IFNULL(visibility, 0) <> 2 OR user_id = ?)", userId)
                    .orderBy("create_time", false)
                    .orderBy("id", false)
                    .limit(pageSize);
            applyActiveStorageProvider(qw);
            if (beforeId != null) {
                qw.and("id < ?", beforeId);
            }
            applySearch(qw, q);
            return filterFeedExclusions(buildPostList(qw, userId), userId);
        }
        return listDiscoverRanked(userId, beforeId, pageSize);
    }

    @Override
    public List<ShortVideoTopicVO> listHotTopics(Integer limit) {
        int size = normalizeHotTopicLimit(limit);
        List<ShortVideoTopic> rows = topicMapper.selectListByQuery(
                buildPublicTopicQueryWrapper()
                        .limit(size));
        return toTopicVOs(rows);
    }

    @Override
    public List<ShortVideoPostVO> listHotVideos(Long userId, Integer limit) {
        int size = normalizeHotVideoLimit(limit);
        int candidateSize = Math.min(size * HOT_VIDEO_CANDIDATE_MULTIPLIER, HOT_VIDEO_MAX_CANDIDATES);
        QueryWrapper qw = QueryWrapper.create()
                .eq("deleted", 0)
                .and("(IFNULL(visibility, 0) <> 2 OR user_id = ?)", userId)
                .orderBy("id", false)
                .limit(candidateSize);
        applyActiveStorageProvider(qw);
        List<ShortVideoPost> candidates = postMapper.selectListByQuery(qw);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        candidates = filterExcludedPosts(candidates, userId);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        decryptPosts(candidates);
        Set<Long> postIds = candidates.stream().map(ShortVideoPost::getId).collect(Collectors.toSet());
        Map<Long, Integer> commentCountMap = loadCommentCountMap(postIds);
        Map<Long, PostInteractionStats> interactionMap = loadInteractionStatsMap(postIds, userId);
        List<ShortVideoPost> ranked = candidates.stream()
                .sorted(Comparator
                        .comparingDouble((ShortVideoPost post) -> hotScore(
                                post,
                                interactionMap.getOrDefault(post.getId(), EMPTY_INTERACTION),
                                commentCountMap.getOrDefault(post.getId(), 0)))
                        .reversed()
                        .thenComparing(ShortVideoPost::getId, Comparator.reverseOrder()))
                .limit(size)
                .collect(Collectors.toCollection(ArrayList::new));
        return buildPostList(ranked, userId);
    }

    @Override
    public PageResultVO<ShortVideoTopicVO> listTopicPlaza(Integer page, Integer limit) {
        int pageNo = normalizePage(page);
        int size = normalizeTopicPlazaLimit(limit);
        QueryWrapper qw = buildPublicTopicQueryWrapper();
        long total = topicMapper.selectCountByQuery(qw);
        qw.limit((pageNo - 1L) * size, size);
        List<ShortVideoTopic> rows = topicMapper.selectListByQuery(qw);
        return PageResultVO.of(toTopicVOs(rows), pageNo, size, total);
    }

    @Override
    public ShortVideoTopicVO getTopic(String name) {
        String normalized = ShortVideoHashtagSupport.normalize(name);
        if (normalized.isBlank()) {
            throw new CustomException(400, "invalid topic name");
        }
        ShortVideoTopic topic = topicMapper.selectOneByQuery(
                QueryWrapper.create().eq("name", normalized));
        if (topic == null) {
            throw new CustomException(404, "话题不存在");
        }
        if (topic.getStatus() != null && topic.getStatus() == 0) {
            throw new CustomException(404, "话题不存在");
        }
        return toTopicVO(topic);
    }

    private List<ShortVideoPostVO> listDiscoverRanked(Long userId, Long beforeId, int pageSize) {
        int candidateSize = Math.min(pageSize * DISCOVER_CANDIDATE_MULTIPLIER, DISCOVER_MAX_CANDIDATES);
        QueryWrapper qw = QueryWrapper.create()
                .eq("deleted", 0)
                .and("(IFNULL(visibility, 0) <> 2 OR user_id = ?)", userId)
                .orderBy("id", false)
                .limit(candidateSize);
        applyActiveStorageProvider(qw);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        List<ShortVideoPost> candidates = postMapper.selectListByQuery(qw);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        candidates = filterExcludedPosts(candidates, userId);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        decryptPosts(candidates);
        Set<Long> postIds = candidates.stream().map(ShortVideoPost::getId).collect(Collectors.toSet());
        Set<Long> authorIds = candidates.stream().map(ShortVideoPost::getUserId).collect(Collectors.toSet());
        Map<Long, Integer> commentCountMap = loadCommentCountMap(postIds);
        Map<Long, PostInteractionStats> interactionMap = loadInteractionStatsMap(postIds, null);
        Set<Long> following = loadFollowingSet(userId, authorIds);
        Set<Long> friendIds = getFriendIds(userId);

        List<ShortVideoPost> ranked = candidates.stream()
                .sorted(Comparator
                        .comparingDouble((ShortVideoPost post) -> discoverScore(
                                post,
                                interactionMap.getOrDefault(post.getId(), EMPTY_INTERACTION),
                                commentCountMap.getOrDefault(post.getId(), 0),
                                following.contains(post.getUserId()),
                                friendIds.contains(post.getUserId()),
                                userId))
                        .reversed()
                        .thenComparing(ShortVideoPost::getId, Comparator.reverseOrder()))
                .limit(pageSize)
                .collect(Collectors.toCollection(ArrayList::new));
        ranked = diversifyDiscoverAuthors(ranked);
        return buildPostList(ranked, userId);
    }

    private double hotScore(ShortVideoPost post, PostInteractionStats interaction, int commentCount) {
        long plays = post.getPlayCount() != null ? post.getPlayCount() : 0L;
        long shares = post.getShareCount() != null ? post.getShareCount() : 0L;
        double engagement = plays
                + interaction.likeCount() * 3D
                + shares * 5D
                + commentCount * 2D;
        double timeDecay = 1D;
        if (post.getCreateTime() != null) {
            double hours = Math.max(0D, Duration.between(post.getCreateTime().toInstant(), Instant.now()).toHours());
            timeDecay = 1D / (1D + hours / 24D);
        }
        return engagement * timeDecay;
    }

    private double discoverScore(
            ShortVideoPost post,
            PostInteractionStats interaction,
            int commentCount,
            boolean followingAuthor,
            boolean friendAuthor,
            Long viewerId) {
        long plays = post.getPlayCount() != null ? post.getPlayCount() : 0L;
        long shares = post.getShareCount() != null ? post.getShareCount() : 0L;
        double engagement = plays
                + interaction.likeCount() * 3D
                + shares * 5D
                + commentCount * 2D;
        double timeDecay = 1D;
        if (post.getCreateTime() != null) {
            double hours = Math.max(0D, Duration.between(post.getCreateTime().toInstant(), Instant.now()).toHours());
            timeDecay = 1D / (1D + hours / 24D);
        }
        double boost = 1D;
        if (followingAuthor) {
            boost *= DISCOVER_FOLLOWING_BOOST;
        } else if (friendAuthor) {
            boost *= DISCOVER_FRIEND_BOOST;
        }
        double jitter = ThreadLocalRandom.current().nextDouble(0D, 0.05D);
        double score = engagement * timeDecay * boost + jitter;
        if (viewerId != null && hasRecentPlay(viewerId, post.getId())) {
            score *= DISCOVER_SEEN_PENALTY;
        }
        if (post.getCreateTime() != null) {
            double hours = Math.max(0D, Duration.between(post.getCreateTime().toInstant(), Instant.now()).toHours());
            if (hours <= DISCOVER_FRESH_HOURS) {
                score *= DISCOVER_FRESH_BOOST;
            }
        }
        return score;
    }

    private List<ShortVideoPost> diversifyDiscoverAuthors(List<ShortVideoPost> ranked) {
        if (ranked.size() < 2) {
            return ranked;
        }
        List<ShortVideoPost> result = new ArrayList<>(ranked);
        for (int i = 1; i < result.size(); i++) {
            Long prevAuthor = result.get(i - 1).getUserId();
            if (!Objects.equals(prevAuthor, result.get(i).getUserId())) {
                continue;
            }
            int swapIndex = -1;
            for (int j = i + 1; j < result.size(); j++) {
                if (!Objects.equals(prevAuthor, result.get(j).getUserId())) {
                    swapIndex = j;
                    break;
                }
            }
            if (swapIndex > 0) {
                ShortVideoPost current = result.get(i);
                result.set(i, result.get(swapIndex));
                result.set(swapIndex, current);
            }
        }
        return result;
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
        applyActiveStorageProvider(qw);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        return filterFeedExclusions(buildPostList(qw, userId), userId);
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
        applyActiveStorageProvider(qw);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        return filterFeedExclusions(buildPostList(qw, userId), userId);
    }

    @Override
    public List<ShortVideoFollowingUserVO> listFollowingUsers(Long userId, Long beforeId, Integer limit) {
        int pageSize = normalizeLimit(limit);
        QueryWrapper qw = QueryWrapper.create()
                .eq("follower_id", userId)
                .eq("deleted", 0)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        List<ShortVideoFollow> follows = followMapper.selectListByQuery(qw);
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> followeeIds = follows.stream()
                .map(ShortVideoFollow::getFolloweeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysUser> users = loadUsers(followeeIds);
        List<ShortVideoFollowingUserVO> result = new ArrayList<>();
        for (ShortVideoFollow follow : follows) {
            SysUser user = users.get(follow.getFolloweeId());
            if (user == null) {
                continue;
            }
            result.add(ShortVideoFollowingUserVO.builder()
                    .followId(follow.getId())
                    .userId(follow.getFolloweeId())
                    .nickname(user.getNickname())
                    .avatar(mediaUrlService.resolveUserAvatar(user.getId(), user.getAvatar()))
                    .postCount(countVisiblePostsByUser(userId, follow.getFolloweeId()))
                    .build());
        }
        return result;
    }

    @Override
    public int countFollowingUsers(Long userId) {
        long count = followMapper.selectCountByQuery(
                QueryWrapper.create().eq("follower_id", userId).eq("deleted", 0));
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    @Override
    public List<ShortVideoFollowerUserVO> listFollowerUsers(
            Long viewerId, Long targetUserId, Long beforeId, Integer limit) {
        requireUser(targetUserId);
        int pageSize = normalizeLimit(limit);
        QueryWrapper qw = QueryWrapper.create()
                .eq("followee_id", targetUserId)
                .eq("deleted", 0)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        List<ShortVideoFollow> follows = followMapper.selectListByQuery(qw);
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> followerIds = follows.stream()
                .map(ShortVideoFollow::getFollowerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysUser> users = loadUsers(followerIds);
        Set<Long> following = loadFollowingSet(viewerId, followerIds);
        List<ShortVideoFollowerUserVO> result = new ArrayList<>();
        for (ShortVideoFollow follow : follows) {
            SysUser user = users.get(follow.getFollowerId());
            if (user == null) {
                continue;
            }
            result.add(ShortVideoFollowerUserVO.builder()
                    .followId(follow.getId())
                    .userId(follow.getFollowerId())
                    .nickname(user.getNickname())
                    .avatar(mediaUrlService.resolveUserAvatar(user.getId(), user.getAvatar()))
                    .postCount(countVisiblePostsByUser(viewerId, follow.getFollowerId()))
                    .followingAuthor(following.contains(follow.getFollowerId()))
                    .build());
        }
        return result;
    }

    @Override
    public List<ShortVideoPostVO> listByUser(Long userId, Long targetUserId, Long beforeId, Integer limit) {
        boolean self = Objects.equals(userId, targetUserId);
        boolean friend = self || getFriendIds(userId).contains(targetUserId);
        int pageSize = normalizeLimit(limit);

        QueryWrapper qw = QueryWrapper.create()
                .eq("user_id", targetUserId)
                .eq("deleted", 0);
        applyActiveStorageProvider(qw);
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
    public ShortVideoAuthorVO getAuthorProfile(Long viewerId, Long targetUserId) {
        SysUser user = requireUser(targetUserId);
        int postCount = countVisiblePostsByUser(viewerId, targetUserId);
        int followingCount = countFollowingUsers(targetUserId);
        int followerCount = countFollowers(targetUserId);
        boolean followingAuthor = loadFollowingSet(viewerId, Set.of(targetUserId)).contains(targetUserId);
        return ShortVideoAuthorVO.builder()
                .userId(targetUserId)
                .nickname(user.getNickname())
                .avatar(mediaUrlService.resolveUserAvatar(user.getId(), user.getAvatar()))
                .postCount(postCount)
                .followingCount(followingCount)
                .followerCount(followerCount)
                .followingAuthor(followingAuthor)
                .build();
    }

    private int countVisiblePostsByUser(Long viewerId, Long targetUserId) {
        boolean self = Objects.equals(viewerId, targetUserId);
        boolean friend = self || getFriendIds(viewerId).contains(targetUserId);
        QueryWrapper qw = QueryWrapper.create()
                .eq("user_id", targetUserId)
                .eq("deleted", 0);
        applyActiveStorageProvider(qw);
        if (!self) {
            if (friend) {
                qw.and("IFNULL(visibility, 0) <> 2");
            } else {
                qw.and("IFNULL(visibility, 0) = 0");
            }
        }
        long count = postMapper.selectCountByQuery(qw);
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private int countFollowers(Long userId) {
        long count = followMapper.selectCountByQuery(
                QueryWrapper.create().eq("followee_id", userId).eq("deleted", 0));
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    @Override
    public List<ShortVideoPostVO> listFavorites(Long userId, Long beforeId, Integer limit) {
        int pageSize = normalizeLimit(limit);
        QueryWrapper favQw = QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            ShortVideoFavorite anchor = favoriteMapper.selectOneByQuery(
                    QueryWrapper.create().eq("user_id", userId).eq("post_id", beforeId));
            if (anchor != null) {
                favQw.and("id < ?", anchor.getId());
            }
        }
        List<ShortVideoFavorite> favoriteRows = favoriteMapper.selectListByQuery(favQw);
        if (favoriteRows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderedPostIds = favoriteRows.stream()
                .map(ShortVideoFavorite::getPostId)
                .collect(Collectors.toList());
        Set<Long> postIdSet = new LinkedHashSet<>(orderedPostIds);

        QueryWrapper postQw = QueryWrapper.create()
                .in("id", new ArrayList<>(postIdSet))
                .eq("deleted", 0);
        applyActiveStorageProvider(postQw);
        List<ShortVideoPost> posts = postMapper.selectListByQuery(postQw);
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        decryptPosts(posts);
        Map<Long, ShortVideoPost> postMap = posts.stream()
                .collect(Collectors.toMap(ShortVideoPost::getId, p -> p, (a, b) -> a));
        Map<Long, PostInteractionStats> interactionMap = loadInteractionStatsMap(postIdSet, userId);
        Map<Long, Integer> commentCountMap = loadCommentCountMap(postIdSet);
        Map<Long, List<String>> topicsByPost = loadTopicsByPostIds(postIdSet);
        Set<Long> authorIds = posts.stream().map(ShortVideoPost::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(authorIds);
        Set<Long> following = loadFollowingSet(userId, authorIds);
        Set<Long> friendIds = getFriendIds(userId);

        List<ShortVideoPostVO> result = new ArrayList<>();
        for (Long postId : orderedPostIds) {
            ShortVideoPost post = postMap.get(postId);
            if (post == null || !canViewPost(post, userId, friendIds)) {
                continue;
            }
            SysUser author = users.get(post.getUserId());
            result.add(toPostVO(
                    post,
                    author,
                    interactionMap.getOrDefault(postId, EMPTY_INTERACTION),
                    Collections.emptyList(),
                    userId,
                    following.contains(post.getUserId()),
                    commentCountMap.getOrDefault(postId, 0),
                    topicsByPost.getOrDefault(postId, List.of())));
        }
        return result;
    }

    @Override
    public List<ShortVideoPostVO> listLikes(Long userId, Long beforeId, Integer limit) {
        int pageSize = normalizeLimit(limit);
        QueryWrapper likeQw = QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            ShortVideoLike anchor = likeMapper.selectOneByQuery(
                    QueryWrapper.create().eq("user_id", userId).eq("post_id", beforeId));
            if (anchor != null) {
                likeQw.and("id < ?", anchor.getId());
            }
        }
        List<ShortVideoLike> likeRows = likeMapper.selectListByQuery(likeQw);
        if (likeRows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderedPostIds = likeRows.stream()
                .map(ShortVideoLike::getPostId)
                .collect(Collectors.toList());
        Set<Long> postIdSet = new LinkedHashSet<>(orderedPostIds);

        QueryWrapper postQw = QueryWrapper.create()
                .in("id", new ArrayList<>(postIdSet))
                .eq("deleted", 0);
        applyActiveStorageProvider(postQw);
        List<ShortVideoPost> posts = postMapper.selectListByQuery(postQw);
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        decryptPosts(posts);
        Map<Long, ShortVideoPost> postMap = posts.stream()
                .collect(Collectors.toMap(ShortVideoPost::getId, p -> p, (a, b) -> a));
        Map<Long, PostInteractionStats> interactionMap = loadInteractionStatsMap(postIdSet, userId);
        Map<Long, Integer> commentCountMap = loadCommentCountMap(postIdSet);
        Map<Long, List<String>> topicsByPost = loadTopicsByPostIds(postIdSet);
        Set<Long> authorIds = posts.stream().map(ShortVideoPost::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(authorIds);
        Set<Long> following = loadFollowingSet(userId, authorIds);
        Set<Long> friendIds = getFriendIds(userId);

        List<ShortVideoPostVO> result = new ArrayList<>();
        for (Long postId : orderedPostIds) {
            ShortVideoPost post = postMap.get(postId);
            if (post == null || !canViewPost(post, userId, friendIds)) {
                continue;
            }
            SysUser author = users.get(post.getUserId());
            result.add(toPostVO(
                    post,
                    author,
                    interactionMap.getOrDefault(postId, EMPTY_INTERACTION),
                    Collections.emptyList(),
                    userId,
                    following.contains(post.getUserId()),
                    commentCountMap.getOrDefault(postId, 0),
                    topicsByPost.getOrDefault(postId, List.of())));
        }
        return result;
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
    public void favorite(Long userId, Long postId) {
        assertCanInteract(userId, postId);
        ShortVideoFavorite existing = favoriteMapper.selectOneByQuery(
                QueryWrapper.create().eq("post_id", postId).eq("user_id", userId));
        if (existing != null) {
            return;
        }
        LogicDeleteManager.execWithoutLogicDelete(() ->
                favoriteMapper.deleteByQuery(
                        QueryWrapper.create().eq("post_id", postId).eq("user_id", userId)));
        try {
            favoriteMapper.insert(ShortVideoFavorite.builder().postId(postId).userId(userId).build());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            /* ignore */
        }
    }

    @Override
    @Transactional
    public void unfavorite(Long userId, Long postId) {
        favoriteMapper.deleteByQuery(
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
        String imageKey = dto.getImageKey() != null ? dto.getImageKey().trim() : "";
        if (raw.isEmpty() && imageKey.isEmpty()) {
            throw new CustomException(400, "评论内容不能为空");
        }
        SensitiveHit hit = raw.isEmpty()
                ? null
                : filterSensitiveOrThrow(userId, raw, SysReviewTask.TARGET_SHORT_VIDEO_COMMENT, null);
        List<Long> mentions = sanitizeMentions(dto.getMentions(), userId);
        String mentionJson = mentions.isEmpty() ? null : toJson(mentions);
        String imageStorageProvider = null;
        if (!imageKey.isEmpty()) {
            imageKey = authorizeObjectKey(userId, imageKey);
            imageStorageProvider = objectStorageRouter.activeProvider().toWire();
        }

        ShortVideoComment comment = ShortVideoComment.builder()
                .postId(postId)
                .userId(userId)
                .content(hit != null ? hit.text() : "")
                .parentId(dto.getParentId())
                .mentions(mentionJson)
                .imageKey(imageKey.isEmpty() ? null : imageKey)
                .imageStorageProvider(imageStorageProvider)
                .build();
        prepareCommentForStorage(comment);
        commentMapper.insert(comment);
        if (hit != null) {
            enqueueSensitiveAfterPersist(
                    userId, SysReviewTask.TARGET_SHORT_VIDEO_COMMENT, String.valueOf(comment.getId()), hit);
        }

        if (!post.getUserId().equals(userId)) {
            String preview = raw.isEmpty() ? "[图片]" : truncate(raw, 100);
            notifyAuthor(post.getUserId(), userId, "short_video_comment", postId, comment.getId(), preview);
        }
        if (!mentions.isEmpty()) {
            Set<Long> notifyTargets = new LinkedHashSet<>(mentions);
            notifyTargets.remove(userId);
            notifyTargets.remove(post.getUserId());
            String preview = raw.isEmpty() ? "[图片]" : truncate(raw, 100);
            for (Long targetId : notifyTargets) {
                notifyAuthor(targetId, userId, "short_video_mention", postId, comment.getId(), preview);
            }
        }
        String replyToNickname = resolveReplyToNickname(dto.getParentId());
        return toCommentVO(comment, user, replyToNickname, 0, false);
    }

    @Override
    public List<ShortVideoCommentVO> listComments(Long userId, Long postId, Long beforeId, Integer limit) {
        assertCanView(userId, postId);
        int pageSize = normalizeLimit(limit);
        List<ShortVideoComment> comments = loadCommentPage(postId, beforeId, pageSize);
        return buildCommentVOs(comments, userId);
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
        commentLikeMapper.deleteByQuery(QueryWrapper.create().eq("comment_id", commentId));
    }

    @Override
    @Transactional
    public void likeComment(Long userId, Long commentId) {
        ShortVideoComment comment = requireComment(commentId);
        assertCanView(userId, comment.getPostId());
        ShortVideoCommentLike existing = commentLikeMapper.selectOneByQuery(
                QueryWrapper.create().eq("comment_id", commentId).eq("user_id", userId));
        if (existing != null) {
            return;
        }
        LogicDeleteManager.execWithoutLogicDelete(() ->
                commentLikeMapper.deleteByQuery(
                        QueryWrapper.create().eq("comment_id", commentId).eq("user_id", userId)));
        try {
            commentLikeMapper.insert(ShortVideoCommentLike.builder()
                    .commentId(commentId)
                    .userId(userId)
                    .build());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            /* idempotent */
        }
    }

    @Override
    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        ShortVideoComment comment = requireComment(commentId);
        assertCanView(userId, comment.getPostId());
        commentLikeMapper.deleteByQuery(
                QueryWrapper.create().eq("comment_id", commentId).eq("user_id", userId));
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
        String dedupKey = PLAY_DEDUP_KEY_PREFIX + userId + ":" + postId;
        Boolean firstPlay = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", PLAY_DEDUP_TTL);
        if (!Boolean.TRUE.equals(firstPlay)) {
            return;
        }
        ShortVideoPost current = postMapper.selectOneById(postId);
        long next = (current != null && current.getPlayCount() != null ? current.getPlayCount() : 0L) + 1L;
        UpdateChain.of(ShortVideoPost.class)
                .set(ShortVideoPost::getPlayCount, next)
                .where(ShortVideoPost::getId).eq(postId)
                .update();
    }

    @Override
    @Transactional
    public void recordShare(Long userId, Long postId) {
        assertCanView(userId, postId);
        String dedupKey = SHARE_DEDUP_KEY_PREFIX + userId + ":" + postId;
        Boolean firstShare = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", SHARE_DEDUP_TTL);
        if (!Boolean.TRUE.equals(firstShare)) {
            return;
        }
        ShortVideoPost current = postMapper.selectOneById(postId);
        long next = (current != null && current.getShareCount() != null ? current.getShareCount() : 0L) + 1L;
        UpdateChain.of(ShortVideoPost.class)
                .set(ShortVideoPost::getShareCount, next)
                .where(ShortVideoPost::getId).eq(postId)
                .update();
    }

    @Override
    @Transactional
    public void markNotInterested(Long userId, Long postId) {
        assertCanView(userId, postId);
        ShortVideoNotInterested existing = notInterestedMapper.selectOneByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("post_id", postId));
        if (existing != null) {
            return;
        }
        LogicDeleteManager.execWithoutLogicDelete(() ->
                notInterestedMapper.deleteByQuery(
                        QueryWrapper.create().eq("user_id", userId).eq("post_id", postId)));
        try {
            notInterestedMapper.insert(ShortVideoNotInterested.builder()
                    .userId(userId)
                    .postId(postId)
                    .build());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            /* idempotent */
        }
    }

    @Override
    @Transactional
    public void blockAuthor(Long userId, Long authorId) {
        if (Objects.equals(userId, authorId)) {
            throw new CustomException(400, "不能屏蔽自己");
        }
        requireUser(authorId);
        ShortVideoAuthorBlock existing = authorBlockMapper.selectOneByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("author_id", authorId));
        if (existing == null) {
            LogicDeleteManager.execWithoutLogicDelete(() ->
                    authorBlockMapper.deleteByQuery(
                            QueryWrapper.create().eq("user_id", userId).eq("author_id", authorId)));
            try {
                authorBlockMapper.insert(ShortVideoAuthorBlock.builder()
                        .userId(userId)
                        .authorId(authorId)
                        .build());
            } catch (org.springframework.dao.DuplicateKeyException e) {
                /* idempotent */
            }
        }
        unfollow(userId, authorId);
    }

    @Override
    @Transactional
    public void unblockAuthor(Long userId, Long authorId) {
        if (Objects.equals(userId, authorId)) {
            throw new CustomException(400, "不能取消屏蔽自己");
        }
        authorBlockMapper.deleteByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("author_id", authorId));
    }

    @Override
    public List<ShortVideoBlockedUserVO> listBlockedAuthors(Long userId, Long beforeId, Integer limit) {
        int pageSize = normalizeLimit(limit);
        QueryWrapper qw = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("deleted", 0)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(pageSize);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        List<ShortVideoAuthorBlock> blocks = authorBlockMapper.selectListByQuery(qw);
        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> authorIds = blocks.stream()
                .map(ShortVideoAuthorBlock::getAuthorId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysUser> users = loadUsers(authorIds);
        List<ShortVideoBlockedUserVO> result = new ArrayList<>();
        for (ShortVideoAuthorBlock block : blocks) {
            SysUser user = users.get(block.getAuthorId());
            if (user == null) {
                continue;
            }
            result.add(ShortVideoBlockedUserVO.builder()
                    .blockId(block.getId())
                    .userId(block.getAuthorId())
                    .nickname(user.getNickname())
                    .avatar(mediaUrlService.resolveUserAvatar(user.getId(), user.getAvatar()))
                    .postCount(countVisiblePostsByUser(userId, block.getAuthorId()))
                    .build());
        }
        return result;
    }

    @Override
    public int countBlockedAuthors(Long userId) {
        long count = authorBlockMapper.selectCountByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("deleted", 0));
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    @Override
    @Transactional
    public void reportPost(Long reporterId, Long postId, ReportShortVideoDTO dto) {
        ShortVideoPost post = assertCanView(reporterId, postId);
        if (Objects.equals(post.getUserId(), reporterId)) {
            throw new CustomException(400, "不能举报自己的作品");
        }
        SysUser reporter = requireUser(reporterId);
        SysUser author = requireUser(post.getUserId());
        String reporterName = StringUtils.hasText(reporter.getNickname())
                ? reporter.getNickname()
                : reporter.getUsername();
        String authorNickname = StringUtils.hasText(author.getNickname())
                ? author.getNickname()
                : author.getUsername();
        AdminReviewService review = adminReviewService.getIfAvailable();
        if (review == null) {
            throw new CustomException(503, "审核服务暂不可用");
        }
        review.createFromShortVideoReport(
                reporterId,
                reporterName,
                postId,
                post.getUserId(),
                authorNickname,
                dto.getReason(),
                dto.getDetail(),
                dto.getImageKeys());
    }

    @Override
    @Transactional
    public void reportComment(Long reporterId, Long commentId, ReportShortVideoDTO dto) {
        ShortVideoComment comment = requireComment(commentId);
        assertCanView(reporterId, comment.getPostId());
        if (Objects.equals(comment.getUserId(), reporterId)) {
            throw new CustomException(400, "不能举报自己的评论");
        }
        SysUser reporter = requireUser(reporterId);
        SysUser author = requireUser(comment.getUserId());
        String reporterName = StringUtils.hasText(reporter.getNickname())
                ? reporter.getNickname()
                : reporter.getUsername();
        String authorNickname = StringUtils.hasText(author.getNickname())
                ? author.getNickname()
                : author.getUsername();
        AdminReviewService review = adminReviewService.getIfAvailable();
        if (review == null) {
            throw new CustomException(503, "审核服务暂不可用");
        }
        review.createFromShortVideoCommentReport(
                reporterId,
                reporterName,
                comment.getPostId(),
                commentId,
                comment.getUserId(),
                authorNickname,
                dto.getReason(),
                dto.getDetail(),
                dto.getImageKeys());
    }

    @Override
    public Long findPostAuthorId(Long postId) {
        if (postId == null) {
            return null;
        }
        ShortVideoPost post = postMapper.selectOneById(postId);
        return post != null ? post.getUserId() : null;
    }

    @Override
    public Long findCommentAuthorId(Long commentId) {
        if (commentId == null) {
            return null;
        }
        ShortVideoComment comment = commentMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", commentId).eq("deleted", 0));
        return comment != null ? comment.getUserId() : null;
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
        if (contentType.startsWith("video/") && file.getSize() > MAX_VIDEO_BYTES) {
            throw new CustomException(400, "视频文件不能超过100MB");
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
        return fileStorageService.openObjectOnProvider(resolvePlaybackVideoKey(post), post.getStorageProvider());
    }

    @Override
    public FileStorageService.StoredObject openCoverContent(Long userId, Long postId) {
        ShortVideoPost post = assertCanView(userId, postId);
        if (post.getCoverKey() == null || post.getCoverKey().isBlank()) {
            throw new CustomException(404, "封面不存在");
        }
        return fileStorageService.openObjectOnProvider(post.getCoverKey(), post.getStorageProvider());
    }

    @Override
    public FileStorageService.StoredObject openCommentImageContent(Long userId, Long commentId) {
        ShortVideoComment comment = commentMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", commentId).eq("deleted", 0));
        if (comment == null) {
            throw new CustomException(404, "评论不存在");
        }
        if (comment.getImageKey() == null || comment.getImageKey().isBlank()) {
            throw new CustomException(404, "评论图片不存在");
        }
        assertCanView(userId, comment.getPostId());
        String provider = comment.getImageStorageProvider();
        if (provider == null || provider.isBlank()) {
            return fileStorageService.openObject(comment.getImageKey());
        }
        return fileStorageService.openObjectOnProvider(comment.getImageKey(), provider);
    }

    @Override
    @Transactional
    public void adminDeletePost(Long postId) {
        postMapper.deleteById(postId);
        likeMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        favoriteMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
        commentMapper.deleteByQuery(QueryWrapper.create().eq("post_id", postId));
    }

    @Override
    @Transactional
    public void adminDeleteComment(Long commentId) {
        commentLikeMapper.deleteByQuery(QueryWrapper.create().eq("comment_id", commentId));
        commentMapper.deleteById(commentId);
    }

    private List<ShortVideoPostVO> buildPostList(QueryWrapper qw, Long viewerId) {
        List<ShortVideoPost> posts = postMapper.selectListByQuery(qw);
        return buildPostList(posts, viewerId);
    }

    private List<ShortVideoPostVO> buildPostList(List<ShortVideoPost> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        decryptPosts(posts);
        Set<Long> postIds = posts.stream().map(ShortVideoPost::getId).collect(Collectors.toSet());
        Map<Long, PostInteractionStats> interactionMap = loadInteractionStatsMap(postIds, viewerId);
        Map<Long, Integer> commentCountMap = loadCommentCountMap(postIds);
        Map<Long, List<String>> topicsByPost = loadTopicsByPostIds(postIds);
        Set<Long> authorIds = posts.stream().map(ShortVideoPost::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(authorIds);
        Set<Long> following = loadFollowingSet(viewerId, authorIds);
        Set<Long> friendIds = getFriendIds(viewerId);

        List<ShortVideoPostVO> result = new ArrayList<>();
        for (ShortVideoPost post : posts) {
            if (!canViewPost(post, viewerId, friendIds)) {
                continue;
            }
            SysUser author = users.get(post.getUserId());
            boolean followingAuthor = following.contains(post.getUserId());
            result.add(toPostVO(
                    post,
                    author,
                    interactionMap.getOrDefault(post.getId(), EMPTY_INTERACTION),
                    Collections.emptyList(),
                    viewerId,
                    followingAuthor,
                    commentCountMap.getOrDefault(post.getId(), 0),
                    topicsByPost.getOrDefault(post.getId(), List.of())));
        }
        return result;
    }

    /** 列表/详情下发鉴权流式地址，避免批量 COS 预签名导致接口超时 */
    private static String shortVideoMediaApiPath(Long postId, String kind) {
        if (postId == null) {
            return null;
        }
        if ("cover".equals(kind)) {
            return "/short-video/" + postId + "/cover/content";
        }
        return "/short-video/" + postId + "/video/content";
    }

    private static String shortVideoCommentImageApiPath(Long commentId) {
        if (commentId == null) {
            return null;
        }
        return "/short-video/comment/" + commentId + "/image/content";
    }

    private ShortVideoPostVO toPostVO(
            ShortVideoPost post,
            SysUser author,
            PostInteractionStats interaction,
            List<ShortVideoComment> comments,
            Long viewerId,
            boolean followingAuthor,
            int commentCount,
            List<String> topics) {
        decryptPost(post);
        List<ShortVideoCommentVO> commentVOs = buildCommentVOs(comments, viewerId);
        return ShortVideoPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(author != null ? author.getNickname() : null)
                .avatar(author != null ? mediaUrlService.resolveUserAvatar(author.getId(), author.getAvatar()) : null)
                .description(post.getDescription())
                .videoUrl(shortVideoMediaApiPath(post.getId(), "video"))
                .coverUrl(post.getCoverKey() != null ? shortVideoMediaApiPath(post.getId(), "cover") : null)
                .durationMs(post.getDurationMs())
                .visibility(post.getVisibility())
                .playCount(post.getPlayCount() != null ? post.getPlayCount() : 0L)
                .shares(post.getShareCount() != null ? post.getShareCount() : 0L)
                .time(formatTime(post.getCreateTime()))
                .likes(interaction.likeCount())
                .liked(interaction.liked())
                .favorites(interaction.favoriteCount())
                .favorited(interaction.favorited())
                .followingAuthor(followingAuthor)
                .commentCount(commentCount)
                .comments(commentVOs)
                .topics(topics != null ? topics : List.of())
                .build();
    }

    private List<ShortVideoCommentVO> buildCommentVOs(List<ShortVideoComment> comments, Long viewerId) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = comments.stream().map(ShortVideoComment::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(userIds);
        Map<Long, ShortVideoComment> byId = comments.stream()
                .collect(Collectors.toMap(ShortVideoComment::getId, c -> c, (a, b) -> a));
        Set<Long> missingParentIds = comments.stream()
                .map(ShortVideoComment::getParentId)
                .filter(Objects::nonNull)
                .filter(pid -> !byId.containsKey(pid))
                .collect(Collectors.toSet());
        if (!missingParentIds.isEmpty()) {
            List<ShortVideoComment> parents = commentMapper.selectListByQuery(
                    QueryWrapper.create().in("id", new ArrayList<>(missingParentIds)).eq("deleted", 0));
            for (ShortVideoComment parent : parents) {
                byId.put(parent.getId(), parent);
                userIds.add(parent.getUserId());
            }
            users = loadUsers(userIds);
        }
        Set<Long> commentIds = comments.stream().map(ShortVideoComment::getId).collect(Collectors.toSet());
        Map<Long, List<ShortVideoCommentLike>> likesMap = loadCommentLikesMap(commentIds);
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
            List<ShortVideoCommentLike> likes = likesMap.getOrDefault(comment.getId(), Collections.emptyList());
            int likeCount = likes.size();
            boolean liked = viewerId != null && likes.stream().anyMatch(l -> Objects.equals(l.getUserId(), viewerId));
            vos.add(toCommentVO(comment, user, replyToNickname, likeCount, liked));
        }
        return vos;
    }

    private ShortVideoComment requireComment(Long commentId) {
        ShortVideoComment comment = commentMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", commentId).eq("deleted", 0));
        if (comment == null) {
            throw new CustomException(404, "评论不存在");
        }
        return comment;
    }

    private String resolveReplyToNickname(Long parentId) {
        if (parentId == null) {
            return null;
        }
        ShortVideoComment parent = commentMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", parentId).eq("deleted", 0));
        if (parent == null) {
            return null;
        }
        SysUser parentUser = requireUser(parent.getUserId());
        return parentUser.getNickname();
    }

    private ShortVideoCommentVO toCommentVO(
            ShortVideoComment comment,
            SysUser user,
            String replyToNickname,
            int likes,
            boolean liked) {
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
                .imageUrl(comment.getImageKey() != null && !comment.getImageKey().isBlank()
                        ? shortVideoCommentImageApiPath(comment.getId())
                        : null)
                .likes(likes)
                .liked(liked)
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
        assertMatchesActiveStorage(post);
        decryptPost(post);
        if (!canViewPost(post, userId)) {
            throw new CustomException(403, "无权查看该作品");
        }
        return post;
    }

    private boolean canViewPost(ShortVideoPost post, Long viewerId, Set<Long> friendIds) {
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
            return friendIds != null && friendIds.contains(post.getUserId());
        }
        return true;
    }

    private boolean canViewPost(ShortVideoPost post, Long viewerId) {
        return canViewPost(post, viewerId, getFriendIds(viewerId));
    }

    private void preparePostForStorage(ShortVideoPost post) {
        String plainDesc = post.getDescription();
        if (plainDesc == null) {
            plainDesc = "";
            post.setDescription(plainDesc);
        }
        List<String> tags = ShortVideoHashtagSupport.extract(plainDesc);
        String tagText = tags.isEmpty()
                ? null
                : tags.stream().map(tag -> "#" + tag).collect(Collectors.joining(" "));
        post.setSearchText(SearchTextSupport.buildMomentsSearchText(plainDesc, tagText));
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

    private Set<Long> loadBlockedAuthorIds(Long viewerId) {
        List<ShortVideoAuthorBlock> rows = authorBlockMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", viewerId).eq("deleted", 0));
        return rows.stream().map(ShortVideoAuthorBlock::getAuthorId).collect(Collectors.toSet());
    }

    private Set<Long> loadNotInterestedPostIds(Long viewerId) {
        List<ShortVideoNotInterested> rows = notInterestedMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", viewerId).eq("deleted", 0));
        return rows.stream().map(ShortVideoNotInterested::getPostId).collect(Collectors.toSet());
    }

    private List<ShortVideoPost> filterExcludedPosts(List<ShortVideoPost> posts, Long viewerId) {
        if (viewerId == null || posts == null || posts.isEmpty()) {
            return posts == null ? List.of() : posts;
        }
        Set<Long> blocked = loadBlockedAuthorIds(viewerId);
        Set<Long> notInterested = loadNotInterestedPostIds(viewerId);
        if (blocked.isEmpty() && notInterested.isEmpty()) {
            return posts;
        }
        return posts.stream()
                .filter(post -> !blocked.contains(post.getUserId()))
                .filter(post -> !notInterested.contains(post.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<ShortVideoPostVO> filterFeedExclusions(List<ShortVideoPostVO> list, Long viewerId) {
        if (viewerId == null || list == null || list.isEmpty()) {
            return list == null ? List.of() : list;
        }
        Set<Long> blocked = loadBlockedAuthorIds(viewerId);
        Set<Long> notInterested = loadNotInterestedPostIds(viewerId);
        if (blocked.isEmpty() && notInterested.isEmpty()) {
            return list;
        }
        return list.stream()
                .filter(vo -> !blocked.contains(vo.getUserId()))
                .filter(vo -> !notInterested.contains(vo.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<Long, SysUser> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = userMapper.selectListByQuery(
                QueryWrapper.create().in("id", new ArrayList<>(userIds)));
        return users.stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
    }

    private PostInteractionStats loadInteractionStats(Long postId, Long viewerId) {
        return loadInteractionStatsMap(Set.of(postId), viewerId).getOrDefault(postId, EMPTY_INTERACTION);
    }

    private Map<Long, PostInteractionStats> loadInteractionStatsMap(Set<Long> postIds, Long viewerId) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<>(postIds);
        Map<Long, Integer> likeCounts = toCountMap(interactionSqlMapper.countLikesByPostIds(ids));
        Map<Long, Integer> favoriteCounts = toCountMap(interactionSqlMapper.countFavoritesByPostIds(ids));
        Set<Long> likedByViewer = viewerId != null
                ? new HashSet<>(interactionSqlMapper.likedPostIdsByViewer(viewerId, ids))
                : Collections.emptySet();
        Set<Long> favoritedByViewer = viewerId != null
                ? new HashSet<>(interactionSqlMapper.favoritedPostIdsByViewer(viewerId, ids))
                : Collections.emptySet();
        Map<Long, PostInteractionStats> result = new HashMap<>();
        for (Long postId : postIds) {
            result.put(postId, new PostInteractionStats(
                    likeCounts.getOrDefault(postId, 0),
                    favoriteCounts.getOrDefault(postId, 0),
                    likedByViewer.contains(postId),
                    favoritedByViewer.contains(postId)));
        }
        return result;
    }

    private Map<Long, Integer> toCountMap(List<ShortVideoCommentCountRow> rows) {
        Map<Long, Integer> counts = new HashMap<>();
        if (rows == null) {
            return counts;
        }
        for (ShortVideoCommentCountRow row : rows) {
            if (row.getPostId() != null && row.getCount() != null) {
                counts.put(row.getPostId(), row.getCount().intValue());
            }
        }
        return counts;
    }

    private Map<Long, Integer> loadCommentCountMap(Set<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ShortVideoCommentCountRow> rows = commentSqlMapper.countByPostIds(new ArrayList<>(postIds));
        Map<Long, Integer> counts = new HashMap<>();
        for (ShortVideoCommentCountRow row : rows) {
            if (row.getPostId() != null && row.getCount() != null) {
                counts.put(row.getPostId(), row.getCount().intValue());
            }
        }
        return counts;
    }

    private int countComments(Long postId) {
        Long count = commentMapper.selectCountByQuery(
                QueryWrapper.create().eq("post_id", postId).eq("deleted", 0));
        return count != null ? count.intValue() : 0;
    }

    private List<ShortVideoComment> loadCommentPage(Long postId, Long beforeId, int limit) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("post_id", postId)
                .eq("deleted", 0)
                .orderBy("create_time", false)
                .orderBy("id", false)
                .limit(limit);
        if (beforeId != null) {
            qw.and("id < ?", beforeId);
        }
        List<ShortVideoComment> comments = commentMapper.selectListByQuery(qw);
        if (comments.isEmpty()) {
            return comments;
        }
        List<ShortVideoComment> chronological = new ArrayList<>(comments);
        Collections.reverse(chronological);
        return chronological;
    }

    private Map<Long, List<ShortVideoCommentLike>> loadCommentLikesMap(Set<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ShortVideoCommentLike> likes = commentLikeMapper.selectListByQuery(
                QueryWrapper.create().in("comment_id", new ArrayList<>(commentIds)).eq("deleted", 0));
        return likes.stream().collect(Collectors.groupingBy(ShortVideoCommentLike::getCommentId));
    }

    private void applySearch(QueryWrapper qw, String q) {
        if (q == null || q.isBlank()) {
            return;
        }
        String keyword = q.trim();
        if (keyword.startsWith("#") || keyword.startsWith("＃")) {
            String tag = ShortVideoHashtagSupport.normalize(keyword.substring(1));
            if (!tag.isBlank()) {
                ShortVideoTopic topic = topicMapper.selectOneByQuery(
                        QueryWrapper.create().eq("name", tag));
                if (topic == null) {
                    qw.and("1 = 0");
                } else if (topic.getStatus() != null && topic.getStatus() == 0) {
                    qw.and("1 = 0");
                } else {
                    qw.and("id IN (SELECT post_id FROM short_video_post_topic WHERE topic_id = ?)", topic.getId());
                }
                return;
            }
        }
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

    private int normalizeHotTopicLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return HOT_TOPIC_DEFAULT_LIMIT;
        }
        return Math.min(limit, HOT_TOPIC_MAX_LIMIT);
    }

    private int normalizeHotVideoLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return HOT_VIDEO_DEFAULT_LIMIT;
        }
        return Math.min(limit, HOT_VIDEO_MAX_LIMIT);
    }

    private int normalizeTopicPlazaLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 50);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private QueryWrapper buildPublicTopicQueryWrapper() {
        QueryWrapper qw = QueryWrapper.create()
                .eq("status", 1)
                .and("(post_count > 0 OR pinned = 1)");
        applyTopicRankingOrder(qw);
        return qw;
    }

    private void applyTopicRankingOrder(QueryWrapper qw) {
        qw.orderBy(ShortVideoTopic::getPinned, false)
                .orderBy(ShortVideoTopic::getPinOrder, false)
                .orderBy(ShortVideoTopic::getHotScore, false)
                .orderBy(ShortVideoTopic::getId, false);
    }

    private List<ShortVideoTopicVO> toTopicVOs(List<ShortVideoTopic> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ShortVideoTopicVO> result = new ArrayList<>(rows.size());
        for (ShortVideoTopic row : rows) {
            result.add(toTopicVO(row));
        }
        return result;
    }

    private ShortVideoTopicVO toTopicVO(ShortVideoTopic row) {
        if (row == null) {
            return ShortVideoTopicVO.builder().build();
        }
        String displayName = row.getDisplayName();
        if (displayName != null && displayName.isBlank()) {
            displayName = null;
        }
        return ShortVideoTopicVO.builder()
                .name(row.getName())
                .displayName(displayName)
                .postCount(row.getPostCount())
                .pinned(row.getPinned() != null && row.getPinned() == 1)
                .build();
    }

    private String resolveInitialTranscodeStatus() {
        LinkxProperties.ShortVideo shortVideo = linkxProperties.getShortVideo();
        return shortVideo != null && shortVideo.isTranscodeEnabled() ? "pending" : "skipped";
    }

    private String resolvePlaybackVideoKey(ShortVideoPost post) {
        if (post != null
                && "completed".equalsIgnoreCase(post.getTranscodeStatus())
                && StringUtils.hasText(post.getTranscodedVideoKey())) {
            return post.getTranscodedVideoKey();
        }
        return post != null ? post.getVideoKey() : null;
    }

    /** 列表仅展示当前全局 storage_provider 下的作品（各云存储内容互不影响展示）。 */
    private void applyActiveStorageProvider(QueryWrapper qw) {
        qw.eq("storage_provider", objectStorageRouter.activeProvider().toWire());
    }

    private void assertMatchesActiveStorage(ShortVideoPost post) {
        if (post == null || !StringUtils.hasText(post.getStorageProvider())) {
            throw new CustomException(404, "作品不存在");
        }
        String active = objectStorageRouter.activeProvider().toWire();
        if (!active.equalsIgnoreCase(post.getStorageProvider().trim())) {
            throw new CustomException(404, "作品不存在");
        }
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        return user;
    }

    private void notifyAuthor(Long authorId, Long actorId, String type, Long postId, String previewText) {
        notifyAuthor(authorId, actorId, type, postId, null, previewText);
    }

    private void notifyAuthor(Long authorId, Long actorId, String type, Long postId, Long extraId, String previewText) {
        try {
            SysUser actor = userMapper.selectOneById(actorId);
            notificationService.create(
                    authorId,
                    actorId,
                    actor != null ? actor.getNickname() : null,
                    actor != null ? actor.getAvatar() : null,
                    type,
                    postId,
                    extraId,
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

    private void scheduleTranscodeAfterCommit(Long postId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    transcodeAsyncTrigger.trigger(postId);
                }
            });
        } else {
            transcodeAsyncTrigger.trigger(postId);
        }
    }

    private void syncPostTopics(Long postId, String rawDescription) {
        List<String> newTags = ShortVideoHashtagSupport.extract(rawDescription);
        List<ShortVideoPostTopic> existingLinks = postTopicMapper.selectListByQuery(
                QueryWrapper.create().eq("post_id", postId));
        Set<Long> oldTopicIds = existingLinks.stream()
                .map(ShortVideoPostTopic::getTopicId)
                .collect(Collectors.toSet());
        Set<Long> newTopicIds = new LinkedHashSet<>();
        Set<Long> affectedTopicIds = new LinkedHashSet<>();
        for (String tag : newTags) {
            ShortVideoTopic topic = topicMapper.selectOneByQuery(QueryWrapper.create().eq("name", tag));
            if (topic == null) {
                topic = ShortVideoTopic.builder()
                        .name(tag)
                        .postCount(0)
                        .pinned(0)
                        .pinOrder(0)
                        .status(1)
                        .hotScore(0D)
                        .build();
                topicMapper.insert(topic);
            }
            newTopicIds.add(topic.getId());
            if (!oldTopicIds.contains(topic.getId())) {
                postTopicMapper.insert(ShortVideoPostTopic.builder()
                        .postId(postId)
                        .topicId(topic.getId())
                        .build());
                int count = topic.getPostCount() != null ? topic.getPostCount() : 0;
                UpdateChain.of(ShortVideoTopic.class)
                        .set(ShortVideoTopic::getPostCount, count + 1)
                        .where(ShortVideoTopic::getId).eq(topic.getId())
                        .update();
                affectedTopicIds.add(topic.getId());
            }
        }
        for (Long oldTopicId : oldTopicIds) {
            if (!newTopicIds.contains(oldTopicId)) {
                postTopicMapper.deleteByQuery(QueryWrapper.create()
                        .eq("post_id", postId)
                        .eq("topic_id", oldTopicId));
                ShortVideoTopic oldTopic = topicMapper.selectOneById(oldTopicId);
                if (oldTopic != null) {
                    int count = oldTopic.getPostCount() != null ? oldTopic.getPostCount() : 0;
                    UpdateChain.of(ShortVideoTopic.class)
                            .set(ShortVideoTopic::getPostCount, Math.max(0, count - 1))
                            .where(ShortVideoTopic::getId).eq(oldTopicId)
                            .update();
                    affectedTopicIds.add(oldTopicId);
                }
            }
        }
        affectedTopicIds.addAll(oldTopicIds);
        affectedTopicIds.addAll(newTopicIds);
        for (Long topicId : affectedTopicIds) {
            refreshTopicRanking(topicId);
        }
    }

    private void refreshTopicRanking(Long topicId) {
        if (topicId == null) {
            return;
        }
        ShortVideoTopic topic = topicMapper.selectOneById(topicId);
        if (topic == null) {
            return;
        }
        Date lastPostAt = topicSqlMapper.findLastPostAt(topicId);
        int postCount = topic.getPostCount() != null ? topic.getPostCount() : 0;
        double hotScore = ShortVideoTopicHotScore.compute(postCount, lastPostAt);
        UpdateChain.of(ShortVideoTopic.class)
                .set(ShortVideoTopic::getLastPostAt, lastPostAt)
                .set(ShortVideoTopic::getHotScore, hotScore)
                .where(ShortVideoTopic::getId).eq(topicId)
                .update();
    }

    private Map<Long, List<String>> loadTopicsByPostIds(Set<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ShortVideoPostTopic> links = postTopicMapper.selectListByQuery(
                QueryWrapper.create().in("post_id", new ArrayList<>(postIds)));
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> topicIds = links.stream()
                .map(ShortVideoPostTopic::getTopicId)
                .collect(Collectors.toSet());
        List<ShortVideoTopic> topics = topicMapper.selectListByQuery(
                QueryWrapper.create().in("id", new ArrayList<>(topicIds)).eq("status", 1));
        Map<Long, String> topicNames = topics.stream()
                .collect(Collectors.toMap(ShortVideoTopic::getId, ShortVideoTopic::getName, (a, b) -> a));
        Map<Long, List<String>> result = new HashMap<>();
        for (ShortVideoPostTopic link : links) {
            String name = topicNames.get(link.getTopicId());
            if (name != null) {
                result.computeIfAbsent(link.getPostId(), ignored -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private List<String> loadTopicsForPost(Long postId) {
        return loadTopicsByPostIds(Set.of(postId)).getOrDefault(postId, List.of());
    }

    private boolean hasRecentPlay(Long viewerId, Long postId) {
        if (viewerId == null || postId == null) {
            return false;
        }
        String dedupKey = PLAY_DEDUP_KEY_PREFIX + viewerId + ":" + postId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey));
    }
}
