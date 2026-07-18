package com.linkx.server.service;

import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.vo.MomentsCommentVO;
import com.linkx.server.controller.vo.MomentsPostVO;

import java.util.List;

/**
 * 朋友圈服务接口
 */
public interface MomentsService {

    /**
     * 发布动态
     */
    MomentsPostVO publish(Long userId, PublishMomentsDTO dto);

    /**
     * 获取朋友圈动态列表
     */
    List<MomentsPostVO> list(Long userId);

    /**
     * 获取用户发布的动态列表
     */
    List<MomentsPostVO> listByUser(Long userId, Long targetUserId);

    /**
     * 点赞动态
     */
    void like(Long userId, Long postId);

    /**
     * 取消点赞
     */
    void unlike(Long userId, Long postId);

    /**
     * 评论动态
     */
    MomentsCommentVO comment(Long userId, Long postId, CommentMomentsDTO dto);

    /**
     * 删除评论
     */
    void deleteComment(Long userId, Long commentId);

    /**
     * 删除动态
     */
    void delete(Long userId, Long postId);

    /**
     * 上传朋友圈图片
     *
     * @param file 图片文件
     * @return MinIO 对象 key（发布时写入 moments_image.url；展示时再签发预签名 URL）
     */
    String uploadImage(org.springframework.web.multipart.MultipartFile file);
}
