package com.linkx.server.service;

import com.linkx.server.controller.dto.SaveNoteDTO;
import com.linkx.server.controller.vo.NoteFileUploadVO;
import com.linkx.server.controller.vo.NoteVO;

import java.util.List;

/**
 * 笔记服务接口
 */
public interface NoteService {

    /**
     * 获取用户笔记列表
     */
    List<NoteVO> list(Long userId);

    /**
     * 获取单条笔记
     */
    NoteVO get(Long userId, Long noteId);

    /**
     * 创建笔记
     */
    NoteVO create(Long userId, SaveNoteDTO dto);

    /**
     * 更新笔记
     */
    NoteVO update(Long userId, Long noteId, SaveNoteDTO dto);

    /**
     * 删除笔记
     */
    void delete(Long userId, Long noteId);

    /**
     * 上传笔记附件（图片/文件/语音），并登记属主
     */
    NoteFileUploadVO upload(Long userId, org.springframework.web.multipart.MultipartFile file);

    /**
     * 将本人有权的 object key 解析为预签名 URL
     */
    String resolveMediaUrl(Long userId, String fileKey);
}
