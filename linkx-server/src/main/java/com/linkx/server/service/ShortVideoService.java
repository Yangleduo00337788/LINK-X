package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
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
import com.linkx.server.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ShortVideoService {

    ShortVideoPostVO publish(Long userId, PublishShortVideoDTO dto);

    ShortVideoPostVO update(Long userId, Long postId, UpdateShortVideoDTO dto);

    void delete(Long userId, Long postId);

    ShortVideoPostVO getPost(Long userId, Long postId);

    List<ShortVideoPostVO> listDiscover(Long userId, Long beforeId, Integer limit, String q);

    List<ShortVideoTopicVO> listHotTopics(Integer limit);

    List<ShortVideoPostVO> listHotVideos(Long userId, Integer limit);

    PageResultVO<ShortVideoTopicVO> listTopicPlaza(Integer page, Integer limit);

    ShortVideoTopicVO getTopic(String name);

    List<ShortVideoPostVO> listFriends(Long userId, Long beforeId, Integer limit);

    List<ShortVideoPostVO> listFollowing(Long userId, Long beforeId, Integer limit);

    List<ShortVideoFollowingUserVO> listFollowingUsers(
            Long viewerId, Long targetUserId, Long beforeId, Integer limit);

    int countFollowingUsers(Long userId);

    List<ShortVideoFollowerUserVO> listFollowerUsers(Long viewerId, Long targetUserId, Long beforeId, Integer limit);

    List<ShortVideoPostVO> listByUser(Long userId, Long targetUserId, Long beforeId, Integer limit);

    ShortVideoAuthorVO getAuthorProfile(Long viewerId, Long targetUserId);

    List<ShortVideoPostVO> listFavorites(Long userId, Long beforeId, Integer limit);

    List<ShortVideoPostVO> listLikes(Long userId, Long beforeId, Integer limit);

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);

    void favorite(Long userId, Long postId);

    void unfavorite(Long userId, Long postId);

    ShortVideoCommentVO comment(Long userId, Long postId, CommentShortVideoDTO dto);

    List<ShortVideoCommentVO> listComments(Long userId, Long postId, Long beforeId, Integer limit);

    void deleteComment(Long userId, Long commentId);

    void likeComment(Long userId, Long commentId);

    void unlikeComment(Long userId, Long commentId);

    void follow(Long userId, Long targetUserId);

    void unfollow(Long userId, Long targetUserId);

    void recordPlay(Long userId, Long postId);

    void recordShare(Long userId, Long postId);

    void markNotInterested(Long userId, Long postId);

    void blockAuthor(Long userId, Long authorId);

    void unblockAuthor(Long userId, Long authorId);

    List<ShortVideoBlockedUserVO> listBlockedAuthors(Long userId, Long beforeId, Integer limit);

    int countBlockedAuthors(Long userId);

    void reportPost(Long reporterId, Long postId, ReportShortVideoDTO dto);

    void reportComment(Long reporterId, Long commentId, ReportShortVideoDTO dto);

    Long findPostAuthorId(Long postId);

    Long findCommentAuthorId(Long commentId);

    String uploadMedia(Long userId, MultipartFile file);

    FileStorageService.StoredObject openVideoContent(Long userId, Long postId);

    FileStorageService.StoredObject openCoverContent(Long userId, Long postId);

    FileStorageService.StoredObject openCommentImageContent(Long userId, Long commentId);

    void adminDeletePost(Long postId);

    void adminDeleteComment(Long commentId);
}
