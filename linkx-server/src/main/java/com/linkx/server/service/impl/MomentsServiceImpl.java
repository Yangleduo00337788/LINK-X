package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;
import com.linkx.server.entity.*;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.*;
import com.linkx.server.service.MomentsService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    @Transactional
    public MomentsPostVO publish(Long userId, PublishMomentsDTO dto) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }

        MomentsPost post = MomentsPost.builder()
                .userId(userId)
                .content(dto.getContent())
                .build();
        postMapper.insert(post);

        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            int order = 0;
            for (String imageUrl : dto.getImages()) {
                imageMapper.insert(MomentsImage.builder()
                        .postId(post.getId())
                        .url(imageUrl)
                        .sortOrder(order++)
                        .build());
            }
        }

        return toPostVO(post, user, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), userId);
    }

    @Override
    public List<MomentsPostVO> list(Long userId) {
        Set<Long> friendIds = getFriendIds(userId);
        friendIds.add(userId);

        List<MomentsPost> posts = postMapper.selectListByQuery(
                QueryWrapper.create()
                        .in("user_id", new ArrayList<>(friendIds))
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
            result.add(toPostVO(post,
                    targetUser,
                    imagesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    likesMap.getOrDefault(post.getId(), Collections.emptyList()),
                    commentsMap.getOrDefault(post.getId(), Collections.emptyList()),
                    userId));
        }
        return result;
    }

    @Override
    @Transactional
    public void like(Long userId, Long postId) {
        assertPostExists(postId);

        MomentsLike existing = likeMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("post_id", postId)
                        .and("user_id", userId)
        );

        if (existing != null) {
            return;
        }

        likeMapper.insert(MomentsLike.builder()
                .postId(postId)
                .userId(userId)
                .build());
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        likeMapper.deleteByQuery(
                QueryWrapper.create()
                        .eq("post_id", postId)
                        .and("user_id", userId)
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

        MomentsComment comment = MomentsComment.builder()
                .postId(postId)
                .userId(userId)
                .content(dto.getContent())
                .parentId(dto.getParentId())
                .build();
        commentMapper.insert(comment);

        return toCommentVO(comment, user);
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
        MomentsPost post = postMapper.selectOneById(postId);
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
        if (postMapper.selectOneById(postId) == null) {
            throw new CustomException(404, "动态不存在");
        }
    }

    private Set<Long> getFriendIds(Long userId) {
        List<SysUserRelation> relations = sysUserRelationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId)
                        .and("status", 1)
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
        List<String> imageUrls = images.stream()
                .sorted(Comparator.comparingInt(MomentsImage::getSortOrder))
                .map(MomentsImage::getUrl)
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
                    return toCommentVO(c, commenter);
                })
                .collect(Collectors.toList());

        String timeStr = formatTime(post.getCreateTime());

        return MomentsPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(user != null ? user.getNickname() : null)
                .avatar(user != null ? user.getAvatar() : null)
                .content(post.getContent())
                .images(imageUrls)
                .time(timeStr)
                .likes(likes.size())
                .liked(liked)
                .likedBy(likedBy)
                .comments(commentVOs)
                .build();
    }

    private MomentsCommentVO toCommentVO(MomentsComment comment, SysUser user) {
        return MomentsCommentVO.builder()
                .id(comment.getId())
                .userId(comment.getUserId())
                .nickname(user != null ? user.getNickname() : null)
                .content(comment.getContent())
                .time(formatTime(comment.getCreateTime()))
                .build();
    }

    private String formatTime(java.util.Date date) {
        if (date == null) return "";
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIME_FORMATTER);
    }
}
