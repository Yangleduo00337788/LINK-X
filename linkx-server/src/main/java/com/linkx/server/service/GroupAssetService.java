package com.linkx.server.service;

import com.linkx.server.controller.dto.CreateGroupAssetDTO;
import com.linkx.server.controller.vo.GroupAssetVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GroupAssetService {

    List<GroupAssetVO> list(Long userId, Long conversationId, String type);

    GroupAssetVO create(Long userId, Long conversationId, CreateGroupAssetDTO dto);

    GroupAssetVO upload(Long userId, Long conversationId, String type, MultipartFile file, String album);

    void delete(Long userId, Long conversationId, Long assetId);

    /** 鉴权后打开群资源文件流（群成员）。 */
    FileStorageService.StoredObject openAssetContent(Long userId, Long conversationId, Long assetId);

    String getAssetFileName(Long userId, Long conversationId, Long assetId);
}

