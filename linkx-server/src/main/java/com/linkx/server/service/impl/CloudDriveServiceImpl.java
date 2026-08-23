package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
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
import com.linkx.server.common.FileExtensionValidator;
import com.linkx.server.common.PasswordEncoderHolder;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.CloudActivityMapper;
import com.linkx.server.mapper.CloudDriveSqlMapper;
import com.linkx.server.mapper.CloudFileMapper;
import com.linkx.server.mapper.CloudFileTagMapper;
import com.linkx.server.mapper.CloudFolderMapper;
import com.linkx.server.mapper.CloudShareMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.mapper.UserStorageMapper;
import com.linkx.server.mapper.row.FolderChildCountRow;
import com.linkx.server.mapper.row.FolderFileAggRow;
import com.linkx.server.service.CloudDriveService;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.MediaUrlService;
import com.linkx.server.service.ObjectKeyOwnershipService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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

    private static final String SHARE_PWD_FAIL_PREFIX = "linkx:share:pwd:";
    private static final String SHARE_PWD_LOCK_PREFIX = "linkx:share:pwd:lock:";
    private static final int SHARE_PWD_MAX_ATTEMPTS = 5;
    private static final int SHARE_PWD_LOCK_MINUTES = 5;

    private final UserStorageMapper userStorageMapper;
    private final CloudDriveSqlMapper cloudDriveSqlMapper;
    private final CloudFolderMapper cloudFolderMapper;
    private final CloudFileMapper cloudFileMapper;
    private final CloudFileTagMapper cloudFileTagMapper;
    private final CloudShareMapper cloudShareMapper;
    private final CloudActivityMapper cloudActivityMapper;
    private final SysUserMapper sysUserMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;
    private final ObjectKeyOwnershipService objectKeyOwnershipService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public DriveStorageVO getStorage(Long userId) {
        UserStorage storage = ensureStorage(userId);
        return toStorageVO(storage);
    }

    @Override
    @Transactional
    public DriveStorageVO expandStorage(Long userId) {
        UserStorage storage = ensureStorage(userId);
        long quota = storage.getQuotaBytes() != null ? storage.getQuotaBytes() : UserStorage.DEFAULT_QUOTA_BYTES;
        long next = quota + UserStorage.EXPAND_STEP_BYTES;
        if (next > UserStorage.MAX_QUOTA_BYTES) {
            throw new CustomException(400, "已达最大扩容上限（60 GB）");
        }
        storage.setQuotaBytes(next);
        int rows = userStorageMapper.casExpandQuota(userId, next, storage.getVersion());
        if (rows == 0) {
            throw new CustomException(409, "扩容冲突，请重试");
        }
        logActivity(userId, CloudActivity.TARGET_STORAGE, userId, "存储空间",
                CloudActivity.ACTION_EXPAND, "扩容 +10 GB");
        return toStorageVO(storage);
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
        String uploaderAvatar = me != null ? mediaUrlService.resolveUserAvatar(me.getId(), me.getAvatar()) : null;
        Map<Long, FolderListStats> folderStats = batchFolderListStats(userId, folders);
        for (CloudFolder f : folders) {
            if (q != null && !f.getName().toLowerCase(Locale.ROOT).contains(q)) continue;
            FolderListStats stats = folderStats.getOrDefault(f.getId(), FolderListStats.EMPTY);
            result.add(DriveItemVO.builder()
                    .kind("folder")
                    .id(f.getId())
                    .name(f.getName())
                    .parentId(f.getParentId())
                    .fileSize(stats.sizeBytes())
                    .childCount(stats.childCount())
                    .uploaderName(uploader)
                    .uploaderAvatar(uploaderAvatar)
                    .createTime(f.getCreateTime() != null ? f.getCreateTime().getTime() : null)
                    .updateTime(f.getUpdateTime() != null ? f.getUpdateTime().getTime() : null)
                    .build());
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
                .deleted(0)
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
                me != null ? mediaUrlService.resolveUserAvatar(me.getId(), me.getAvatar()) : null
        );
    }

    @Override
    @Transactional
    public DriveItemVO upload(Long userId, Long folderId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "文件不能为空");
        }

        // 安全校验：扩展名白名单 + 危险扩展名黑名单
        FileExtensionValidator.assertAllowedExtension(file);

        UserStorage storage = ensureStorage(userId);
        if (folderId != null) {
            requireFolder(userId, folderId);
        }
        long size = file.getSize();
        ensureDriveCapacity(storage, size);

        String key;
        try {
            key = fileStorageService.uploadFile(file);
            objectKeyOwnershipService.claim(userId, key);
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
        int rows = userStorageMapper.casUpdateUsedBytes(userId, size, 1, storage.getVersion());
        if (rows == 0) {
            // CAS 失败：DB 行会随事务回滚，但 MinIO 对象已写入 → 异步删孤儿对象
            fileStorageService.deleteFileAsync(key);
            throw new CustomException(409, "存储信息冲突，请重试");
        }

        SysUser me = sysUserMapper.selectOneById(userId);
        logActivity(userId, CloudActivity.TARGET_FILE, entity.getId(), entity.getName(),
                CloudActivity.ACTION_UPLOAD, "上传文件");
        return toFileVO(
                entity,
                List.of(),
                me != null ? me.getNickname() : null,
                me != null ? mediaUrlService.resolveUserAvatar(me.getId(), me.getAvatar()) : null
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
                me != null ? mediaUrlService.resolveUserAvatar(me.getId(), me.getAvatar()) : null
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
                me != null ? mediaUrlService.resolveUserAvatar(me.getId(), me.getAvatar()) : null
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
        if (CloudShare.TYPE_FILE.equals(type)) {
            CloudFile file = requireFile(userId, targetId);
            targetName = file.getName();
            fileSize = file.getFileSize();
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
                        ? PasswordEncoderHolder.encode(dto.getPassword().trim()) : null)
                .expireAt(expireAt)
                .maxDownloads(dto.getMaxDownloads())
                .downloadCount(0)
                .status(1)
                .build();
        cloudShareMapper.insert(share);
        logActivity(userId, type.equals(CloudShare.TYPE_FILE) ? CloudActivity.TARGET_FILE : CloudActivity.TARGET_FOLDER,
                targetId, targetName, CloudActivity.ACTION_SHARE, "创建分享链接");

        // 创建响应不返回预签名 fileUrl，降低日志/前端持久化泄露面；公开访问走 getPublicShare
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
        // 兼容旧客户端：返回短效预签名；新客户端请用 /share/{token}/content 中转下载
        CloudShare share = requireActiveShare(token, password);
        if (!CloudShare.TYPE_FILE.equals(share.getShareType())) {
            throw new CustomException(400, "仅文件分享支持直接下载");
        }
        // 原子递增下载次数，通过受影响行数判断是否超限，避免 check-then-set TOCTOU 竞态
        int rows = cloudShareMapper.incrementDownloadCount(share.getId());
        if (rows == 0) {
            throw new CustomException(400, "分享下载次数已用尽");
        }
        CloudFile file = cloudFileMapper.selectOneById(share.getTargetId());
        if (file == null) {
            throw new CustomException(404, "文件不存在");
        }
        logActivity(share.getUserId(), CloudActivity.TARGET_FILE, file.getId(), file.getName(),
                CloudActivity.ACTION_DOWNLOAD, "分享下载");
        return mediaUrlService.resolveShare(file.getFileKey());
    }

    @Override
    public FileStorageService.StoredObject openFileContent(Long userId, Long fileId) {
        CloudFile file = requireFile(userId, fileId);
        logActivity(userId, CloudActivity.TARGET_FILE, file.getId(), file.getName(),
                CloudActivity.ACTION_DOWNLOAD, "网盘下载");
        return fileStorageService.openObject(file.getFileKey());
    }

    @Override
    @Transactional
    public FileStorageService.StoredObject openShareContent(String token, String password) {
        CloudShare share = requireActiveShare(token, password);
        if (!CloudShare.TYPE_FILE.equals(share.getShareType())) {
            throw new CustomException(400, "仅文件分享支持直接下载");
        }
        // 原子递增下载次数，通过受影响行数判断是否超限，避免 check-then-set TOCTOU 竞态
        int rows = cloudShareMapper.incrementDownloadCount(share.getId());
        if (rows == 0) {
            throw new CustomException(400, "分享下载次数已用尽");
        }
        CloudFile file = cloudFileMapper.selectOneById(share.getTargetId());
        if (file == null) {
            throw new CustomException(404, "文件不存在");
        }
        logActivity(share.getUserId(), CloudActivity.TARGET_FILE, file.getId(), file.getName(),
                CloudActivity.ACTION_DOWNLOAD, "分享下载");
        return fileStorageService.openObject(file.getFileKey());
    }

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

    /**
     * 当前用量 + 新增字节若超出配额，按 10GiB 步长自动扩容，直到够用或达 60GiB 上限。
     */
    private void ensureDriveCapacity(UserStorage storage, long additionalBytes) {
        long used = storage.getUsedBytes() != null ? storage.getUsedBytes() : 0L;
        long quota = storage.getQuotaBytes() != null ? storage.getQuotaBytes() : UserStorage.DEFAULT_QUOTA_BYTES;
        long need = used + Math.max(0L, additionalBytes);
        if (need <= quota) {
            return;
        }
        long next = quota;
        while (next < need && next + UserStorage.EXPAND_STEP_BYTES <= UserStorage.MAX_QUOTA_BYTES) {
            next += UserStorage.EXPAND_STEP_BYTES;
        }
        if (need > next) {
            throw new CustomException(400, "已达最大存储上限（60 GB）");
        }
        storage.setQuotaBytes(next);
        int expandRows = userStorageMapper.casExpandQuota(storage.getUserId(), next, storage.getVersion());
        if (expandRows == 0) {
            throw new CustomException(409, "扩容冲突，请重试");
        }
        long addedGb = (next - quota) / (1024L * 1024 * 1024);
        logActivity(storage.getUserId(), CloudActivity.TARGET_STORAGE, storage.getUserId(), "存储空间",
                CloudActivity.ACTION_EXPAND, "自动扩容 +" + addedGb + " GB");
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
        if (ancestorId == null || maybeChildId == null) {
            return false;
        }
        if (Objects.equals(ancestorId, maybeChildId)) {
            return true;
        }
        return cloudDriveSqlMapper.countDescendant(userId, ancestorId, maybeChildId) > 0;
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
        int deleteRows = userStorageMapper.casUpdateUsedBytes(userId, -size, -1, storage.getVersion());
        if (deleteRows == 0) {
            // 删除是幂等操作，CAS 失败仅打日志，不回滚文件删除
        }
        logActivity(userId, CloudActivity.TARGET_FILE, file.getId(), file.getName(),
                CloudActivity.ACTION_DELETE, "删除文件");
    }

    private void deleteFolderRecursive(Long userId, CloudFolder folder) {
        List<Long> folderIds = cloudDriveSqlMapper.selectSubtreeFolderIds(userId, folder.getId());
        if (folderIds.isEmpty()) {
            return;
        }
        List<CloudFile> files = cloudFileMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(CloudFile::getUserId).eq(userId)
                        .and(CloudFile::getFolderId).in(folderIds));
        for (CloudFile file : files) {
            softDeleteFile(userId, file);
        }
        for (Long folderId : folderIds) {
            cloudFolderMapper.deleteById(folderId);
        }
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
        FolderListStats stats = batchFolderListStats(userId, List.of(f))
                .getOrDefault(f.getId(), FolderListStats.EMPTY);
        return DriveItemVO.builder()
                .kind("folder")
                .id(f.getId())
                .name(f.getName())
                .parentId(f.getParentId())
                .fileSize(stats.sizeBytes())
                .childCount(stats.childCount())
                .uploaderName(uploader)
                .uploaderAvatar(uploaderAvatar)
                .createTime(f.getCreateTime() != null ? f.getCreateTime().getTime() : null)
                .updateTime(f.getUpdateTime() != null ? f.getUpdateTime().getTime() : null)
                .build();
    }

    /**
     * 批量计算文件夹直属子项数与子树占用；仅查询目标目录子树，避免拉取用户全盘文件/文件夹。
     */
    private Map<Long, FolderListStats> batchFolderListStats(Long userId, List<CloudFolder> folders) {
        if (folders == null || folders.isEmpty()) {
            return Map.of();
        }

        Set<Long> targetIds = folders.stream().map(CloudFolder::getId).collect(Collectors.toSet());
        String targetIdCsv = joinLongIds(targetIds);
        Map<Long, Integer> subfolderCountByParent = new HashMap<>();
        if (!targetIdCsv.isEmpty()) {
            for (FolderChildCountRow row : cloudDriveSqlMapper.countDirectSubfolders(userId, targetIdCsv)) {
                if (row.getParentId() != null && row.getCount() != null) {
                    subfolderCountByParent.put(row.getParentId(), (int) Math.min(row.getCount(), Integer.MAX_VALUE));
                }
            }
        }

        Map<Long, Integer> directFileCountByFolder = new HashMap<>();
        if (!targetIdCsv.isEmpty()) {
            for (FolderChildCountRow row : cloudDriveSqlMapper.countDirectFiles(userId, targetIdCsv)) {
                if (row.getParentId() != null && row.getCount() != null) {
                    directFileCountByFolder.put(row.getParentId(), (int) Math.min(row.getCount(), Integer.MAX_VALUE));
                }
            }
        }

        Map<Long, Long> subtreeSizeByTarget = new HashMap<>();
        for (CloudFolder target : folders) {
            long size = 0L;
            String basePath = target.getPath();
            if (StringUtils.hasText(basePath)) {
                FolderFileAggRow agg = cloudDriveSqlMapper.aggregateSubtreeFiles(userId, basePath);
                if (agg != null && agg.getTotalSize() != null) {
                    size = agg.getTotalSize();
                }
            }
            subtreeSizeByTarget.put(target.getId(), size);
        }

        Map<Long, FolderListStats> result = new HashMap<>();
        for (Long folderId : targetIds) {
            int directChildren = subfolderCountByParent.getOrDefault(folderId, 0)
                    + directFileCountByFolder.getOrDefault(folderId, 0);
            long size = subtreeSizeByTarget.getOrDefault(folderId, 0L);
            result.put(folderId, new FolderListStats(directChildren, size));
        }
        return result;
    }

    private static String joinLongIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private record FolderListStats(int childCount, long sizeBytes) {
        private static final FolderListStats EMPTY = new FolderListStats(0, 0L);
    }

    private DriveItemVO toFileVO(CloudFile f, List<String> tags, String uploader, String uploaderAvatar) {
        return DriveItemVO.builder()
                .kind("file")
                .id(f.getId())
                .name(f.getName())
                .folderId(f.getFolderId())
                .fileSize(f.getFileSize())
                .fileUrl(mediaUrlService.resolveFile(f.getFileKey()))
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
            String failKey = SHARE_PWD_FAIL_PREFIX + share.getId();
            String lockKey = SHARE_PWD_LOCK_PREFIX + share.getId();
            // 锁定期间直接拒绝，防止密码爆破
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
                throw new CustomException(429, "提取码错误次数过多，请 " + SHARE_PWD_LOCK_MINUTES + " 分钟后重试");
            }
            if (!StringUtils.hasText(password) || !PasswordEncoderHolder.matches(password, share.getPasswordHash())) {
                Long count = stringRedisTemplate.opsForValue().increment(failKey);
                if (count != null && count == 1L) {
                    stringRedisTemplate.expire(failKey, Duration.ofMinutes(SHARE_PWD_LOCK_MINUTES));
                }
                if (count != null && count >= SHARE_PWD_MAX_ATTEMPTS) {
                    stringRedisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(SHARE_PWD_LOCK_MINUTES));
                    throw new CustomException(429, "提取码错误次数过多，请 " + SHARE_PWD_LOCK_MINUTES + " 分钟后重试");
                }
                throw new CustomException(403, "提取码错误");
            }
            // 密码校验通过，清除失败计数
            stringRedisTemplate.delete(failKey);
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
                fileUrl = mediaUrlService.resolveShare(file.getFileKey());
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
