package com.linkx.server.service;

import com.linkx.server.controller.dto.CommentMomentsDTO;
import com.linkx.server.controller.dto.PublishMomentsDTO;
import com.linkx.server.controller.dto.UpdateMomentsDTO;
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
     * 更新动态（仅作者）
     */
    MomentsPostVO update(Long userId, Long postId, UpdateMomentsDTO dto);

    /**
     * 获取朋友圈动态列表（支持游标分页与关键词搜索）
     *
     * @param beforeId 上一页最后一条 ID，空则从最新开始
     * @param limit    每页条数，默认 20，最大 50
     * @param q        可选，按内容模糊搜索
     */
    List<MomentsPostVO> list(Long userId, Long beforeId, Integer limit, String q);

    /**
     * 获取用户发布的动态列表
     */
    List<MomentsPostVO> listByUser(Long userId, Long targetUserId, Long beforeId, Integer limit, String q);

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
     * 上传朋友圈图片或视频
     *
     * @param file 媒体文件
     * @return MinIO 对象 key（发布时写入 moments_image.url；展示时再签发预签名 URL）
     */
    String uploadImage(Long userId, org.springframework.web.multipart.MultipartFile file);
}
