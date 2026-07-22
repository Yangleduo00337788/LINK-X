package com.linkx.server.service;

import com.linkx.server.controller.dto.CreateDriveFolderDTO;
import com.linkx.server.controller.dto.CreateDriveShareDTO;
import com.linkx.server.controller.dto.DriveBatchDTO;
import com.linkx.server.controller.dto.UpdateDriveItemDTO;
import com.linkx.server.controller.vo.DriveActivityVO;
import com.linkx.server.controller.vo.DriveItemVO;
import com.linkx.server.controller.vo.DriveShareVO;
import com.linkx.server.controller.vo.DriveStorageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CloudDriveService {

    DriveStorageVO getStorage(Long userId);

    DriveStorageVO expandStorage(Long userId);

    List<DriveItemVO> listItems(Long userId, Long folderId, String keyword);

    List<DriveItemVO> breadcrumb(Long userId, Long folderId);

    DriveItemVO createFolder(Long userId, CreateDriveFolderDTO dto);

    DriveItemVO upload(Long userId, Long folderId, MultipartFile file);

    DriveItemVO getFile(Long userId, Long fileId);

    DriveItemVO updateFile(Long userId, Long fileId, UpdateDriveItemDTO dto);

    DriveItemVO updateFolder(Long userId, Long folderId, UpdateDriveItemDTO dto);

    void deleteFile(Long userId, Long fileId);

    void deleteFolder(Long userId, Long folderId);

    void batchDelete(Long userId, DriveBatchDTO dto);

    void batchMove(Long userId, DriveBatchDTO dto);

    List<String> addTag(Long userId, Long fileId, String tagName);

    List<String> removeTag(Long userId, Long fileId, String tagName);

    List<DriveActivityVO> listActivities(Long userId, Long fileId, int limit);

    DriveShareVO createShare(Long userId, CreateDriveShareDTO dto);

    void revokeShare(Long userId, Long shareId);

    DriveShareVO getPublicShare(String token, String password);

    String downloadPublicShare(String token, String password);

    /**
     * 鉴权后打开网盘文件流（所有者）。
     */
    FileStorageService.StoredObject openFileContent(Long userId, Long fileId);

    /**
     * 公开分享下载：校验 token/密码/次数后返回对象流（不再返回可转发的长签名 URL）。
     */
    FileStorageService.StoredObject openShareContent(String token, String password);
}
