package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminShortVideoCommentQueryDTO;
import com.linkx.server.controller.admin.dto.AdminShortVideoPostQueryDTO;
import com.linkx.server.controller.admin.vo.AdminShortVideoCommentVO;
import com.linkx.server.controller.admin.vo.AdminShortVideoPostVO;
import com.linkx.server.service.FileStorageService;

public interface AdminShortVideoService {

    PageResultVO<AdminShortVideoPostVO> listPosts(AdminShortVideoPostQueryDTO query);

    AdminShortVideoPostVO postDetail(Long postId);

    void deletePost(Long postId);

    PageResultVO<AdminShortVideoCommentVO> listComments(AdminShortVideoCommentQueryDTO query);

    void deleteComment(Long commentId);

    FileStorageService.StoredObject openVideoContent(Long postId);

    FileStorageService.StoredObject openCoverContent(Long postId);
}
