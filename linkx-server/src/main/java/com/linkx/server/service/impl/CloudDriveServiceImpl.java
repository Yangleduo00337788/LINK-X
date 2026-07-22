package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CreateDriveFolderDTO;
import com.linkx.server.controller.dto.CreateDriveShareDTO;
import com.linkx.server.controller.dto.DriveBatchDTO;
import com.linkx.server.controller.dto.UpdateDriveItemDTO;
import com.linkx.server.controller.vo.DriveActivityVO;
import com.linkx.server.controller.vo.DriveItemVO;
import com.linkx.server.controller.vo.DriveShareVO;
import com.linkx.server.controller.vo.DriveStorageVO;
import com.linkx.server.entity.CloudActivity;
import com.linkx.server.entity.CloudFile;
import com.linkx.server.entity.CloudFileTag;
import com.linkx.server.entity.CloudFolder;
import com.linkx.server.entity.CloudShare;
import com.linkx.server.entity.SysUser;
import com.linkx.server.entity.UserStorage;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.CloudActivityMapper;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.CloudFileTagMapper;
import com.linkx.server.mapper.CloudFolderMapper;
import com.linkx.server.mapper.CloudShareMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.UserStorageMapper;
import com.linkx.server.service.CloudDriveService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CloudDriveServiceImpl implements CloudDriveService {

    private final UserStorageMapper userStorageMapper;
    private final CloudFolderMapper cloudFolderMapper;
    private final CloudFileMapper cloudFileMapper;
    private final CloudFileTagMapper cloudFileTagMapper;
    private final CloudShareMapper cloudShareMapper;
    private final CloudActivityMapper cloudActivityMapper;
    private final SysUserMapper sysUserMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;

    @Override
    public DriveStorageVO getStorage(Long userId) {
        UserStorage storage = ensureStorage(userId);
        return toStorageVO(storage);
    }

    @Override
    @Transactional
    public DriveStorageVO expandStorage(Long userId) {
        throw new CustomException(403, "扩容需开通会员，请先购买 VIP / SVIP");
    }

    @Override
    public List<DriveItemVO> listItems(Long userId, Long folderId, String keyword) {
        ensureStorage(userId);
        if (folderId != null) {
            requireFolder(userId, folderId);
        }

        List<DriveItemVO> result = new ArrayList<>();
        String q = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;

        QueryWrapper folderQw = QueryWrapper.create().where(CloudFolder::getUserId).eq(userId);
        if (folderId == null) {
            folderQw.and(CloudFolder::getParentId).isNull();
        } else {
            folderQw.and(CloudFolder::getParentId).eq(folderId);
        }
        List<CloudFolder> folders = cloudFolderMapper.selectListByQuery(folderQw);
        SysUser me = sysUserMapper.selectOneById(userId);
        String uploader = me != null ? me.getNickname() : null;
        String uploaderAvatar = me != null ? mediaUrlService.resolve(me.getAvatar()) : null;
        for (CloudFolder f : folders) {
            if (q != null && !f.getName().toLowerCase(Locale.ROOT).contains(q)) continue;
            result.add(toFolderVO(userId, f, uploader, uploaderAvatar));
        }

        QueryWrapper fileQw = QueryWrapper.create().where(CloudFile::getUserId).eq(userId);
        if (folderId == null) {
            fileQw.and(CloudFile::getFolderId).isNull();
        } else {
            fileQw.and(CloudFile::getFolderId).eq(folderId);
        }
        if (q != null) {
            // 搜索时扩大到全盘
            fileQw = QueryWrapper.create().where(CloudFile::getUserId).eq(userId);
        }
        List<CloudFile> files = cloudFileMapper.selectListByQuery(fileQw.orderBy(CloudFile::getUpdateTime, false));
        Map<Long, List<String>> tagMap = loadTags(userId, files.stream().map(CloudFile::getId).collect(Collectors.toSet()));
        for (CloudFile f : files) {
            if (q != null && !f.getName().toLowerCase(Locale.ROOT).contains(q)
                    && !(f.getFileName() != null && f.getFileName().toLowerCase(Locale.ROOT).contains(q))) {
                continue;
            }
            result.add(toFileVO(f, tagMap.getOrDefault(f.getId(), List.of()), uploader, uploaderAvatar));
        }

        result.sort(Comparator
                .comparing((DriveItemVO i) -> !"folder".equals(i.getKind()))
                .thenComparing(DriveItemVO::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public List<DriveItemVO> breadcrumb(Long userId, Long folderId) {
        LinkedList<DriveItemVO> chain = new LinkedList<>();
        Long cur = folderId;
        int guard = 0;
        while (cur != null && guard++ < 64) {
            CloudFolder f = requireFolder(userId, cur);
            chain.addFirst(DriveItemVO.builder()
                    .kind("folder")
                    .id(f.getId())
                    .name(f.getName())
                    .parentId(f.getParentId())
                    .build());
            cur = f.getParentId();
        }
        return chain;
    }

    @Override
    @Transactional
    public DriveItemVO createFolder(Long userId, CreateDriveFolderDTO dto) {
        ensureStorage(userId);
        Long parentId = parseNullableId(dto.getParentId());
        String name = dto.getName().trim();
        String parentPath = "/";
        if (parentId != null) {
            CloudFolder parent = requireFolder(userId, parentId);
            parentPath = parent.getPath();
        }
        assertNoDuplicateFolder(userId, parentId, name, null);

        String path = parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;
        Date now = new Date();
        CloudFolder folder = CloudFolder.builder()
                .userId(userId)
                .parentId(parentId)
                .name(name)
                .path(path)
                .sortOrder(0)
                .createTime(now)
                .updateTime(now)
                .build();
        cloudFolderMapper.insert(folder);
        CloudFolder saved = cloudFolderMapper.selectOneById(folder.getId());
        logActivity(userId, CloudActivity.TARGET_FOLDER, folder.getId(), name,
                CloudActivity.ACTION_CREATE, "新建文件夹");
        SysUser me = sysUserMapper.selectOneById(userId);
        return toFolderVO(
                userId,
                saved != null ? saved : folder,
                me != null ? me.getNickname() : null,
                me != null ? mediaUrlService.resolve(me.getAvatar()) : null
        );
    }

    @Override
    @Transactional
    public DriveItemVO upload(Long userId, Long folderId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "文件不能为空");
        }
        UserStorage storage = ensureStorage(userId);
        if (folderId != null) {
            requireFolder(userId, folderId);
        }
        long size = file.getSize();
        if (storage.getUsedBytes() + size > storage.getQuotaBytes()) {
            throw new CustomException(400, "存储空间不足，请先扩容");
        }

        String key;
        try {
            key = fileStorageService.uploadFile(file);
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, e.getMessage());
        } catch (RuntimeException e) {
            throw new CustomException(500, "文件上传失败");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = extOf(original);
        Date now = new Date();
        CloudFile entity = CloudFile.builder()
                .userId(userId)
                .folderId(folderId)
                .name(original)
                .fileName(original)
                .fileSize(size)
                .fileKey(key)
                .contentType(file.getContentType())
                .ext(ext)
                .category(categorize(ext, file.getContentType()))
                .createTime(now)
                .updateTime(now)
                .build();
        cloudFileMapper.insert(entity);
        CloudFile saved = cloudFileMapper.selectOneById(entity.getId());
        if (saved != null) {
            entity = saved;
        }

        storage.setUsedBytes(storage.getUsedBytes() + size);
        storage.setFileCount(storage.getFileCount() + 1);
        storage.setVersion(storage.getVersion() + 1);
        userStorageMapper.update(storage);

        SysUser me = sysUserMapper.selectOneById(userId);
        logActivity(userId, CloudActivity.TARGET_FILE, entity.getId(), entity.getName(),
                CloudActivity.ACTION_UPLOAD, "上传文件");
        return toFileVO(
                entity,
                List.of(),
                me != null ? me.getNickname() : null,
                me != null ? mediaUrlService.resolve(me.getAvatar()) : null
        );
    }

    @Override
    public DriveItemVO getFile(Long userId, Long fileId) {
        CloudFile file = requireFile(userId, fileId);
        SysUser me = sysUserMapper.selectOneById(userId);
        Map<Long, List<String>> tags = loadTags(userId, Set.of(fileId));
        return toFileVO(
                file,
                tags.getOrDefault(fileId, List.of()),
                me != null ? me.getNickname() : null,
                me != null ? mediaUrlService.resolve(me.getAvatar()) : null
        );
    }

    @Override
    @Transactional
    public DriveItemVO updateFile(Long userId, Long fileId, UpdateDriveItemDTO dto) {
        CloudFile file = requireFile(userId, fileId);
        boolean changed = false;
        if (StringUtils.hasText(dto.getName())) {
            file.setName(dto.getName().trim());
            changed = true;
            logActivity(userId, CloudActivity.TARGET_FILE, fileId, file.getName(),
                    CloudActivity.ACTION_RENAME, "重命名");
        }
        if (dto.getFolderId() != null) {
            Long target = "".equals(dto.getFolderId()) ? null : parseNullableId(dto.getFolderId());
            if (target != null) requireFolder(userId, target);
            file.setFolderId(target);
            changed = true;
            logActivity(userId, CloudActivity.TARGET_FILE, fileId, file.getName(),
                    CloudActivity.ACTION_MOVE, "移动文件");
        }
        if (dto.getDescription() != null) {
            file.setDescription(dto.getDescription().trim());
            changed = true;
        }
        if (changed) {
            cloudFileMapper.update(file);
        }
        return getFile(userId, fileId);
    }

    @Override
    @Transactional
    public DriveItemVO updateFolder(Long userId, Long folderId, UpdateDriveItemDTO dto) {
        CloudFolder folder = requireFolder(userId, folderId);
        if (StringUtils.hasText(dto.getName())) {
            String name = dto.getName().trim();
            assertNoDuplicateFolder(userId, folder.getParentId(), name, folderId);
            folder.setName(name);
            // 简化：仅更新自身 path 末段
            String parentPath = "/";
            if (folder.getParentId() != null) {
                parentPath = requireFolder(userId, folder.getParentId()).getPath();
            }
            folder.setPath(parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name);
            cloudFolderMapper.update(folder);
            logActivity(userId, CloudActivity.TARGET_FOLDER, folderId, name,
                    CloudActivity.ACTION_RENAME, "重命名文件夹");
        }
        if (dto.getFolderId() != null) {
            Long newParent = "".equals(dto.getFolderId()) ? null : parseNullableId(dto.getFolderId());
            if (Objects.equals(newParent, folderId)) {
                throw new CustomException(400, "不能移动到自身");
            }
            if (newParent != null) {
                requireFolder(userId, newParent);
                // 防止移到自己的子树
                if (isDescendant(userId, folderId, newParent)) {
                    throw new CustomException(400, "不能移动到子文件夹中");
                }
            }
            folder.setParentId(newParent);
            String parentPath = "/";
            if (newParent != null) {
                parentPath = requireFolder(userId, newParent).getPath();
            }
            folder.setPath(parentPath.endsWith("/") ? parentPath + folder.getName() : parentPath + "/" + folder.getName());
            cloudFolderMapper.update(folder);
            logActivity(userId, CloudActivity.TARGET_FOLDER, folderId, folder.getName(),
                    CloudActivity.ACTION_MOVE, "移动文件夹");
        }
        SysUser me = sysUserMapper.selectOneById(userId);
        return toFolderVO(
                userId,
                folder,
                me != null ? me.getNickname() : null,
                me != null ? mediaUrlService.resolve(me.getAvatar()) : null
        );
    }

    @Override
    @Transactional
    public void deleteFile(Long userId, Long fileId) {
        CloudFile file = requireFile(userId, fileId);
        softDeleteFile(userId, file);
    }

    @Override
    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        CloudFolder folder = requireFolder(userId, folderId);
        deleteFolderRecursive(userId, folder);
    }

    @Override
    @Transactional
    public void batchDelete(Long userId, DriveBatchDTO dto) {
        for (DriveBatchDTO.DriveBatchItem item : normalizeBatch(dto)) {
            Long id = parseId(item.getId());
            if ("folder".equals(item.getKind())) {
                deleteFolder(userId, id);
            } else {
                deleteFile(userId, id);
            }
        }
    }

    @Override
    @Transactional
    public void batchMove(Long userId, DriveBatchDTO dto) {
        Long target = parseNullableId(dto.getTargetFolderId());
        if (target != null) requireFolder(userId, target);
        UpdateDriveItemDTO upd = new UpdateDriveItemDTO();
        upd.setFolderId(target == null ? "" : String.valueOf(target));
        for (DriveBatchDTO.DriveBatchItem item : normalizeBatch(dto)) {
            Long id = parseId(item.getId());
            if ("folder".equals(item.getKind())) {
                updateFolder(userId, id, upd);
            } else {
                updateFile(userId, id, upd);
            }
        }
    }

    @Override
    @Transactional
    public List<String> addTag(Long userId, Long fileId, String tagName) {
        requireFile(userId, fileId);
        String tag = tagName.trim();
        if (!StringUtils.hasText(tag)) {
            throw new CustomException(400, "标签不能为空");
        }
        long exists = cloudFileTagMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(CloudFileTag::getFileId).eq(fileId)
                        .and(CloudFileTag::getTagName).eq(tag)
        );
        if (exists == 0) {
            cloudFileTagMapper.insert(CloudFileTag.builder()
                    .userId(userId)
                    .fileId(fileId)
                    .tagName(tag)
                    .build());
            logActivity(userId, CloudActivity.TARGET_FILE, fileId, tag,
                    CloudActivity.ACTION_TAG, "添加标签：" + tag);
        }
        return listTagNames(fileId);
    }

    @Override
    @Transactional
    public List<String> removeTag(Long userId, Long fileId, String tagName) {
        requireFile(userId, fileId);
        cloudFileTagMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(CloudFileTag::getFileId).eq(fileId)
                        .and(CloudFileTag::getUserId).eq(userId)
                        .and(CloudFileTag::getTagName).eq(tagName)
        );
        logActivity(userId, CloudActivity.TARGET_FILE, fileId, tagName,
                CloudActivity.ACTION_TAG, "移除标签：" + tagName);
        return listTagNames(fileId);
    }

    @Override
    public List<DriveActivityVO> listActivities(Long userId, Long fileId, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        QueryWrapper qw = QueryWrapper.create().where(CloudActivity::getUserId).eq(userId);
        if (fileId != null) {
            qw.and(CloudActivity::getTargetType).eq(CloudActivity.TARGET_FILE)
                    .and(CloudActivity::getTargetId).eq(fileId);
        }
        return cloudActivityMapper.selectListByQuery(
                        qw.orderBy(CloudActivity::getCreateTime, false).limit(cap)
                ).stream()
                .map(a -> DriveActivityVO.builder()
                        .id(a.getId())
                        .targetType(a.getTargetType())
                        .targetId(a.getTargetId())
                        .targetName(a.getTargetName())
                        .action(a.getAction())
                        .detail(a.getDetail())
                        .createTime(a.getCreateTime() != null ? a.getCreateTime().getTime() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DriveShareVO createShare(Long userId, CreateDriveShareDTO dto) {
        String type = dto.getShareType().trim().toLowerCase(Locale.ROOT);
        Long targetId = parseId(dto.getTargetId());
        String targetName;
        Long fileSize = null;
        String fileUrl = null;
        if (CloudShare.TYPE_FILE.equals(type)) {
            CloudFile file = requireFile(userId, targetId);
            targetName = file.getName();
            fileSize = file.getFileSize();
            fileUrl = mediaUrlService.resolve(file.getFileKey());
        } else if (CloudShare.TYPE_FOLDER.equals(type)) {
            CloudFolder folder = requireFolder(userId, targetId);
            targetName = folder.getName();
        } else {
            throw new CustomException(400, "不支持的分享类型");
        }

        Date expireAt = null;
        if (dto.getExpireHours() != null && dto.getExpireHours() > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR_OF_DAY, dto.getExpireHours());
            expireAt = cal.getTime();
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        CloudShare share = CloudShare.builder()
                .userId(userId)
                .shareType(type)
                .targetId(targetId)
                .token(token)
                .passwordHash(StringUtils.hasText(dto.getPassword())
                        ? BCrypt.hashpw(dto.getPassword().trim(), BCrypt.gensalt(12)) : null)
                .expireAt(expireAt)
                .maxDownloads(dto.getMaxDownloads())
                .downloadCount(0)
                .status(1)
                .build();
        cloudShareMapper.insert(share);
        logActivity(userId, type.equals(CloudShare.TYPE_FILE) ? CloudActivity.TARGET_FILE : CloudActivity.TARGET_FOLDER,
                targetId, targetName, CloudActivity.ACTION_SHARE, "创建分享链接");

        return DriveShareVO.builder()
                .id(share.getId())
                .shareType(type)
                .targetId(targetId)
                .token(token)
                .shareUrl("/cloud/share/" + token)
                .hasPassword(share.getPasswordHash() != null)
                .expireAt(expireAt != null ? expireAt.getTime() : null)
                .maxDownloads(share.getMaxDownloads())
                .downloadCount(0)
                .targetName(targetName)
                .fileSize(fileSize)
                .fileUrl(fileUrl)
                .build();
    }

    @Override
    @Transactional
    public void revokeShare(Long userId, Long shareId) {
        CloudShare share = cloudShareMapper.selectOneById(shareId);
        if (share == null || !Objects.equals(share.getUserId(), userId)) {
            throw new CustomException(404, "分享不存在");
        }
        share.setStatus(0);
        cloudShareMapper.update(share);
    }

    @Override
    public DriveShareVO getPublicShare(String token, String password) {
        CloudShare share = requireActiveShare(token, password);
        return buildPublicShareVO(share);
    }

    @Override
    @Transactional
    public String downloadPublicShare(String token, String password) {
        CloudShare share = requireActiveShare(token, password);
        if (!CloudShare.TYPE_FILE.equals(share.getShareType())) {
            throw new CustomException(400, "仅文件分享支持直接下载");
        }
        if (share.getMaxDownloads() != null && share.getDownloadCount() >= share.getMaxDownloads()) {
            throw new CustomException(400, "分享下载次数已用尽");
        }
        CloudFile file = cloudFileMapper.selectOneById(share.getTargetId());
        if (file == null) {
            throw new CustomException(404, "文件不存在");
        }
        share.setDownloadCount(share.getDownloadCount() + 1);
        cloudShareMapper.update(share);
        logActivity(share.getUserId(), CloudActivity.TARGET_FILE, file.getId(), file.getName(),
                CloudActivity.ACTION_DOWNLOAD, "分享下载");
        return mediaUrlService.resolve(file.getFileKey());
    }

    // ── helpers ──────────────────────────────────────────────

    private UserStorage ensureStorage(Long userId) {
        UserStorage storage = userStorageMapper.selectOneById(userId);
        if (storage != null) return storage;
        storage = UserStorage.builder()
                .userId(userId)
                .quotaBytes(UserStorage.DEFAULT_QUOTA_BYTES)
                .usedBytes(0L)
                .fileCount(0)
                .version(0)
                .build();
        try {
            userStorageMapper.insert(storage);
        } catch (Exception ignored) {
            storage = userStorageMapper.selectOneById(userId);
        }
        if (storage == null) {
            throw new CustomException(500, "初始化存储失败");
        }
        return storage;
    }

    private DriveStorageVO toStorageVO(UserStorage s) {
        long used = s.getUsedBytes() != null ? s.getUsedBytes() : 0;
        long quota = s.getQuotaBytes() != null ? s.getQuotaBytes() : UserStorage.DEFAULT_QUOTA_BYTES;
        double pct = quota <= 0 ? 0 : Math.min(100.0, used * 100.0 / quota);
        return DriveStorageVO.builder()
                .usedBytes(used)
                .quotaBytes(quota)
                .fileCount(s.getFileCount() != null ? s.getFileCount() : 0)
                .usedPercent(Math.round(pct * 10) / 10.0)
                .build();
    }

    private CloudFolder requireFolder(Long userId, Long folderId) {
        CloudFolder folder = cloudFolderMapper.selectOneById(folderId);
        if (folder == null || !Objects.equals(folder.getUserId(), userId)) {
            throw new CustomException(404, "文件夹不存在");
        }
        return folder;
    }

    private CloudFile requireFile(Long userId, Long fileId) {
        CloudFile file = cloudFileMapper.selectOneById(fileId);
        if (file == null || !Objects.equals(file.getUserId(), userId)) {
            throw new CustomException(404, "文件不存在");
        }
        return file;
    }

    private void assertNoDuplicateFolder(Long userId, Long parentId, String name, Long excludeId) {
        QueryWrapper qw = QueryWrapper.create()
                .where(CloudFolder::getUserId).eq(userId)
                .and(CloudFolder::getName).eq(name);
        if (parentId == null) qw.and(CloudFolder::getParentId).isNull();
        else qw.and(CloudFolder::getParentId).eq(parentId);
        List<CloudFolder> list = cloudFolderMapper.selectListByQuery(qw);
        for (CloudFolder f : list) {
            if (excludeId != null && Objects.equals(f.getId(), excludeId)) continue;
            throw new CustomException(400, "同目录下已存在同名文件夹");
        }
    }

    private boolean isDescendant(Long userId, Long ancestorId, Long maybeChildId) {
        Long cur = maybeChildId;
        int guard = 0;
        while (cur != null && guard++ < 64) {
            if (Objects.equals(cur, ancestorId)) return true;
            CloudFolder f = cloudFolderMapper.selectOneById(cur);
            if (f == null || !Objects.equals(f.getUserId(), userId)) return false;
            cur = f.getParentId();
        }
        return false;
    }

    private void softDeleteFile(Long userId, CloudFile file) {
        cloudFileMapper.deleteById(file.getId());
        cloudFileTagMapper.deleteByQuery(QueryWrapper.create().where(CloudFileTag::getFileId).eq(file.getId()));
        try {
            fileStorageService.deleteFile(file.getFileKey());
        } catch (Exception ignored) {
            // 对象可能已不存在
        }
        UserStorage storage = ensureStorage(userId);
        long size = file.getFileSize() != null ? file.getFileSize() : 0;
        storage.setUsedBytes(Math.max(0, storage.getUsedBytes() - size));
        storage.setFileCount(Math.max(0, storage.getFileCount() - 1));
        storage.setVersion(storage.getVersion() + 1);
        userStorageMapper.update(storage);
        logActivity(userId, CloudActivity.TARGET_FILE, file.getId(), file.getName(),
                CloudActivity.ACTION_DELETE, "删除文件");
    }

    private void deleteFolderRecursive(Long userId, CloudFolder folder) {
        List<CloudFolder> children = cloudFolderMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(CloudFolder::getUserId).eq(userId)
                        .and(CloudFolder::getParentId).eq(folder.getId())
        );
        for (CloudFolder child : children) {
            deleteFolderRecursive(userId, child);
        }
        List<CloudFile> files = cloudFileMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(CloudFile::getUserId).eq(userId)
                        .and(CloudFile::getFolderId).eq(folder.getId())
        );
        for (CloudFile f : files) {
            softDeleteFile(userId, f);
        }
        cloudFolderMapper.deleteById(folder.getId());
        logActivity(userId, CloudActivity.TARGET_FOLDER, folder.getId(), folder.getName(),
                CloudActivity.ACTION_DELETE, "删除文件夹");
    }

    private List<DriveBatchDTO.DriveBatchItem> normalizeBatch(DriveBatchDTO dto) {
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            return dto.getItems();
        }
        if (dto.getIds() == null || dto.getIds().isEmpty() || !StringUtils.hasText(dto.getKind())) {
            throw new CustomException(400, "请选择要操作的项目");
        }
        List<DriveBatchDTO.DriveBatchItem> list = new ArrayList<>();
        for (String id : dto.getIds()) {
            DriveBatchDTO.DriveBatchItem item = new DriveBatchDTO.DriveBatchItem();
            item.setKind(dto.getKind());
            item.setId(id);
            list.add(item);
        }
        return list;
    }

    private DriveItemVO toFolderVO(Long userId, CloudFolder f, String uploader, String uploaderAvatar) {
        long childFolders = cloudFolderMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(CloudFolder::getUserId).eq(userId)
                        .and(CloudFolder::getParentId).eq(f.getId())
        );
        long childFiles = cloudFileMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(CloudFile::getUserId).eq(userId)
                        .and(CloudFile::getFolderId).eq(f.getId())
        );
        return DriveItemVO.builder()
                .kind("folder")
                .id(f.getId())
                .name(f.getName())
                .parentId(f.getParentId())
                .fileSize(calcFolderSizeBytes(userId, f))
                .childCount((int) (childFolders + childFiles))
                .uploaderName(uploader)
                .uploaderAvatar(uploaderAvatar)
                .createTime(f.getCreateTime() != null ? f.getCreateTime().getTime() : null)
                .updateTime(f.getUpdateTime() != null ? f.getUpdateTime().getTime() : null)
                .build();
    }

    /** 统计文件夹及其子目录下全部文件占用（字节） */
    private long calcFolderSizeBytes(Long userId, CloudFolder folder) {
        String path = folder.getPath() != null ? folder.getPath() : "/";
        String prefix = path.endsWith("/") ? path : path + "/";
        List<CloudFolder> descendants = cloudFolderMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(CloudFolder::getUserId).eq(userId)
                        .and(CloudFolder::getPath).like(prefix + "%")
        );
        Set<Long> folderIds = new HashSet<>();
        folderIds.add(folder.getId());
        for (CloudFolder d : descendants) {
            folderIds.add(d.getId());
        }
        List<CloudFile> files = cloudFileMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(CloudFile::getUserId).eq(userId)
                        .and(CloudFile::getFolderId).in(folderIds)
                        .select(CloudFile::getFileSize)
        );
        long sum = 0L;
        for (CloudFile file : files) {
            if (file.getFileSize() != null) {
                sum += file.getFileSize();
            }
        }
        return sum;
    }

    private DriveItemVO toFileVO(CloudFile f, List<String> tags, String uploader, String uploaderAvatar) {
        return DriveItemVO.builder()
                .kind("file")
                .id(f.getId())
                .name(f.getName())
                .folderId(f.getFolderId())
                .fileSize(f.getFileSize())
                .fileUrl(mediaUrlService.resolve(f.getFileKey()))
                .contentType(f.getContentType())
                .ext(f.getExt())
                .category(f.getCategory())
                .description(f.getDescription())
                .tags(tags)
                .uploaderName(uploader)
                .uploaderAvatar(uploaderAvatar)
                .createTime(f.getCreateTime() != null ? f.getCreateTime().getTime() : null)
                .updateTime(f.getUpdateTime() != null ? f.getUpdateTime().getTime() : null)
                .build();
    }

    private Map<Long, List<String>> loadTags(Long userId, Set<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return Map.of();
        List<CloudFileTag> tags = cloudFileTagMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(CloudFileTag::getUserId).eq(userId)
                        .and(CloudFileTag::getFileId).in(fileIds)
        );
        return tags.stream().collect(Collectors.groupingBy(
                CloudFileTag::getFileId,
                Collectors.mapping(CloudFileTag::getTagName, Collectors.toList())
        ));
    }

    private List<String> listTagNames(Long fileId) {
        return cloudFileTagMapper.selectListByQuery(
                        QueryWrapper.create().where(CloudFileTag::getFileId).eq(fileId)
                ).stream()
                .map(CloudFileTag::getTagName)
                .collect(Collectors.toList());
    }

    private void logActivity(Long userId, String targetType, Long targetId, String name, String action, String detail) {
        cloudActivityMapper.insert(CloudActivity.builder()
                .userId(userId)
                .targetType(targetType)
                .targetId(targetId)
                .targetName(name)
                .action(action)
                .detail(detail)
                .createTime(new Date())
                .build());
    }

    private CloudShare requireActiveShare(String token, String password) {
        CloudShare share = cloudShareMapper.selectOneByQuery(
                QueryWrapper.create().where(CloudShare::getToken).eq(token)
        );
        if (share == null || share.getStatus() == null || share.getStatus() != 1) {
            throw new CustomException(404, "分享不存在或已失效");
        }
        if (share.getExpireAt() != null && share.getExpireAt().before(new Date())) {
            throw new CustomException(400, "分享已过期");
        }
        if (share.getPasswordHash() != null) {
            if (!StringUtils.hasText(password) || !BCrypt.checkpw(password, share.getPasswordHash())) {
                throw new CustomException(403, "提取码错误");
            }
        }
        return share;
    }

    private DriveShareVO buildPublicShareVO(CloudShare share) {
        String targetName = null;
        Long fileSize = null;
        String fileUrl = null;
        if (CloudShare.TYPE_FILE.equals(share.getShareType())) {
            CloudFile file = cloudFileMapper.selectOneById(share.getTargetId());
            if (file != null) {
                targetName = file.getName();
                fileSize = file.getFileSize();
                fileUrl = mediaUrlService.resolve(file.getFileKey());
            }
        } else {
            CloudFolder folder = cloudFolderMapper.selectOneById(share.getTargetId());
            if (folder != null) targetName = folder.getName();
        }
        return DriveShareVO.builder()
                .id(share.getId())
                .shareType(share.getShareType())
                .targetId(share.getTargetId())
                .token(share.getToken())
                .shareUrl("/cloud/share/" + share.getToken())
                .hasPassword(share.getPasswordHash() != null)
                .expireAt(share.getExpireAt() != null ? share.getExpireAt().getTime() : null)
                .maxDownloads(share.getMaxDownloads())
                .downloadCount(share.getDownloadCount())
                .targetName(targetName)
                .fileSize(fileSize)
                .fileUrl(fileUrl)
                .build();
    }

    private static String extOf(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String categorize(String ext, String contentType) {
        if (contentType != null && contentType.startsWith("image/")) return "image";
        if (contentType != null && (contentType.startsWith("audio/") || contentType.startsWith("video/"))) return "media";
        String e = ext == null ? "" : ext;
        if (Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(e)) return "image";
        if (Set.of("mp4", "mov", "avi", "mkv", "webm", "mp3", "wav").contains(e)) return "media";
        if (Set.of("doc", "docx", "pdf", "ppt", "pptx", "xls", "xlsx", "txt", "md").contains(e)) return "document";
        return "other";
    }

    private static Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            throw new CustomException(400, "无效的 ID");
        }
    }

    private static Long parseNullableId(String id) {
        if (!StringUtils.hasText(id)) return null;
        return parseId(id.trim());
    }
}
