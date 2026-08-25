package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.AdminKeywordQuery;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminShortVideoCommentQueryDTO;
import com.linkx.server.controller.admin.dto.AdminShortVideoPostQueryDTO;
import com.linkx.server.controller.admin.vo.AdminShortVideoCommentVO;
import com.linkx.server.controller.admin.vo.AdminShortVideoPostVO;
import com.linkx.server.entity.ShortVideoComment;
import com.linkx.server.entity.ShortVideoCommentLike;
import com.linkx.server.entity.ShortVideoPost;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.ShortVideoCommentLikeMapper;
import com.linkx.server.mapper.ShortVideoCommentMapper;
import com.linkx.server.mapper.ShortVideoCommentSqlMapper;
import com.linkx.server.mapper.ShortVideoInteractionSqlMapper;
import com.linkx.server.mapper.ShortVideoPostMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.row.ShortVideoCommentCountRow;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.ShortVideoService;
import com.linkx.server.service.admin.AdminShortVideoService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminShortVideoServiceImpl implements AdminShortVideoService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ShortVideoPostMapper postMapper;
    private final ShortVideoCommentMapper commentMapper;
    private final ShortVideoCommentLikeMapper commentLikeMapper;
    private final ShortVideoCommentSqlMapper commentSqlMapper;
    private final ShortVideoInteractionSqlMapper interactionSqlMapper;
    private final SysUserMapper userMapper;
    private final ShortVideoService shortVideoService;
    private final FileStorageService fileStorageService;
    private final MessageContentCipher messageContentCipher;

    @Override
    public PageResultVO<AdminShortVideoPostVO> listPosts(AdminShortVideoPostQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildPostQuery(query);
        qw.orderBy(ShortVideoPost::getCreateTime, false);
        long total = postMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<ShortVideoPost> posts = postMapper.selectListByQuery(qw);
        return PageResultVO.of(toPostVOs(posts), page, size, total);
    }

    @Override
    public AdminShortVideoPostVO postDetail(Long postId) {
        ShortVideoPost post = requirePost(postId);
        return toPostVOs(List.of(post)).get(0);
    }

    @Override
    public void deletePost(Long postId) {
        requirePost(postId);
        shortVideoService.adminDeletePost(postId);
    }

    @Override
    public PageResultVO<AdminShortVideoCommentVO> listComments(AdminShortVideoCommentQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildCommentQuery(query);
        qw.orderBy(ShortVideoComment::getCreateTime, false);
        long total = commentMapper.selectCountByQuery(qw);
        qw.limit((page - 1L) * size, size);
        List<ShortVideoComment> comments = commentMapper.selectListByQuery(qw);
        return PageResultVO.of(toCommentVOs(comments), page, size, total);
    }

    @Override
    public void deleteComment(Long commentId) {
        ShortVideoComment comment = commentMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", commentId).eq("deleted", 0));
        if (comment == null) {
            throw new CustomException(404, "评论不存在");
        }
        shortVideoService.adminDeleteComment(commentId);
    }

    @Override
    public FileStorageService.StoredObject openVideoContent(Long postId) {
        ShortVideoPost post = requirePost(postId);
        return fileStorageService.openObjectOnProvider(resolvePlaybackVideoKey(post), post.getStorageProvider());
    }

    @Override
    public FileStorageService.StoredObject openCoverContent(Long postId) {
        ShortVideoPost post = requirePost(postId);
        if (post.getCoverKey() == null || post.getCoverKey().isBlank()) {
            throw new CustomException(404, "封面不存在");
        }
        return fileStorageService.openObjectOnProvider(post.getCoverKey(), post.getStorageProvider());
    }

    private QueryWrapper buildPostQuery(AdminShortVideoPostQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create().eq("deleted", 0);
        String kw = AdminKeywordQuery.forLike(query.getKeyword());
        if (kw != null) {
            qw.and("(search_text LIKE ? OR CAST(id AS CHAR) LIKE ?)", "%" + kw + "%", "%" + kw + "%");
        }
        if (query.getUserId() != null) {
            qw.and(ShortVideoPost::getUserId).eq(query.getUserId());
        }
        if (query.getVisibility() != null) {
            qw.and(ShortVideoPost::getVisibility).eq(query.getVisibility());
        }
        if (StringUtils.hasText(query.getTranscodeStatus())) {
            qw.and(ShortVideoPost::getTranscodeStatus).eq(query.getTranscodeStatus().trim());
        }
        if (query.getStartTime() != null) {
            qw.and(ShortVideoPost::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(ShortVideoPost::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    private QueryWrapper buildCommentQuery(AdminShortVideoCommentQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create().eq("deleted", 0);
        String kw = AdminKeywordQuery.forLike(query.getKeyword());
        if (kw != null) {
            qw.and("(content LIKE ? OR CAST(id AS CHAR) LIKE ?)", "%" + kw + "%", "%" + kw + "%");
        }
        if (query.getPostId() != null) {
            qw.and(ShortVideoComment::getPostId).eq(query.getPostId());
        }
        if (query.getUserId() != null) {
            qw.and(ShortVideoComment::getUserId).eq(query.getUserId());
        }
        if (query.getStartTime() != null) {
            qw.and(ShortVideoComment::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(ShortVideoComment::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    private List<AdminShortVideoPostVO> toPostVOs(List<ShortVideoPost> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        for (ShortVideoPost post : posts) {
            decryptPost(post);
        }
        Set<Long> postIds = posts.stream().map(ShortVideoPost::getId).collect(Collectors.toSet());
        Set<Long> userIds = posts.stream().map(ShortVideoPost::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(userIds);
        Map<Long, Integer> commentCounts = toCountMap(commentSqlMapper.countByPostIds(new ArrayList<>(postIds)));
        Map<Long, Integer> likeCounts = toCountMap(interactionSqlMapper.countLikesByPostIds(new ArrayList<>(postIds)));
        Map<Long, Integer> favoriteCounts = toCountMap(interactionSqlMapper.countFavoritesByPostIds(new ArrayList<>(postIds)));

        List<AdminShortVideoPostVO> result = new ArrayList<>();
        for (ShortVideoPost post : posts) {
            SysUser user = users.get(post.getUserId());
            result.add(AdminShortVideoPostVO.builder()
                    .id(post.getId())
                    .userId(post.getUserId())
                    .username(user != null ? user.getUsername() : null)
                    .nickname(user != null ? user.getNickname() : null)
                    .description(post.getDescription())
                    .visibility(post.getVisibility())
                    .playCount(post.getPlayCount())
                    .shareCount(post.getShareCount())
                    .likeCount(likeCounts.getOrDefault(post.getId(), 0))
                    .favoriteCount(favoriteCounts.getOrDefault(post.getId(), 0))
                    .commentCount(commentCounts.getOrDefault(post.getId(), 0))
                    .durationMs(post.getDurationMs())
                    .transcodeStatus(post.getTranscodeStatus())
                    .videoUrl(adminVideoUrl(post.getId()))
                    .coverUrl(post.getCoverKey() != null && !post.getCoverKey().isBlank()
                            ? adminCoverUrl(post.getId()) : null)
                    .createTime(formatTime(post.getCreateTime()))
                    .build());
        }
        return result;
    }

    private List<AdminShortVideoCommentVO> toCommentVOs(List<ShortVideoComment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }
        for (ShortVideoComment comment : comments) {
            decryptComment(comment);
        }
        Set<Long> userIds = comments.stream().map(ShortVideoComment::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(userIds);
        Set<Long> commentIds = comments.stream().map(ShortVideoComment::getId).collect(Collectors.toSet());
        Map<Long, Integer> likeCounts = loadCommentLikeCounts(commentIds);
        Set<Long> postIds = comments.stream().map(ShortVideoComment::getPostId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ShortVideoPost> postsById = loadPostsById(postIds);

        List<AdminShortVideoCommentVO> result = new ArrayList<>();
        for (ShortVideoComment comment : comments) {
            SysUser user = users.get(comment.getUserId());
            ShortVideoPost post = comment.getPostId() != null ? postsById.get(comment.getPostId()) : null;
            result.add(AdminShortVideoCommentVO.builder()
                    .id(comment.getId())
                    .postId(comment.getPostId())
                    .userId(comment.getUserId())
                    .username(user != null ? user.getUsername() : null)
                    .nickname(user != null ? user.getNickname() : null)
                    .content(comment.getContent())
                    .parentId(comment.getParentId())
                    .likeCount(likeCounts.getOrDefault(comment.getId(), 0))
                    .postCoverUrl(post != null && post.getCoverKey() != null && !post.getCoverKey().isBlank()
                            ? adminCoverUrl(post.getId()) : null)
                    .createTime(formatTime(comment.getCreateTime()))
                    .build());
        }
        return result;
    }

    private Map<Long, Integer> loadCommentLikeCounts(Set<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        List<ShortVideoCommentLike> likes = commentLikeMapper.selectListByQuery(
                QueryWrapper.create().in("comment_id", new ArrayList<>(commentIds)).eq("deleted", 0));
        Map<Long, Integer> counts = new HashMap<>();
        for (ShortVideoCommentLike like : likes) {
            counts.merge(like.getCommentId(), 1, Integer::sum);
        }
        return counts;
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

    private Map<Long, SysUser> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = userMapper.selectListByQuery(
                QueryWrapper.create().in("id", new ArrayList<>(userIds)));
        return users.stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, ShortVideoPost> loadPostsById(Set<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<ShortVideoPost> posts = postMapper.selectListByQuery(
                QueryWrapper.create().in("id", new ArrayList<>(postIds)).eq("deleted", 0));
        return posts.stream().collect(Collectors.toMap(ShortVideoPost::getId, p -> p, (a, b) -> a));
    }

    private ShortVideoPost requirePost(Long postId) {
        ShortVideoPost post = postMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", postId).eq("deleted", 0));
        if (post == null) {
            throw new CustomException(404, "作品不存在");
        }
        return post;
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

    private void decryptComment(ShortVideoComment comment) {
        if (comment == null) {
            return;
        }
        if (messageContentCipher.isEncryptedContent(comment.getContent(), comment.getContentEncVersion())) {
            comment.setContent(messageContentCipher.decryptTextFromStorage(
                    comment.getContent(), comment.getContentEncVersion()));
        }
    }

    private String resolvePlaybackVideoKey(ShortVideoPost post) {
        if (post != null
                && "completed".equalsIgnoreCase(post.getTranscodeStatus())
                && StringUtils.hasText(post.getTranscodedVideoKey())) {
            return post.getTranscodedVideoKey();
        }
        return post != null ? post.getVideoKey() : null;
    }

    private static String adminVideoUrl(Long postId) {
        return "/media/admin-short-video/" + postId + "/video";
    }

    private static String adminCoverUrl(Long postId) {
        return "/media/admin-short-video/" + postId + "/cover";
    }

    private static String formatTime(Date date) {
        if (date == null) {
            return "";
        }
        return TIME_FORMATTER.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
