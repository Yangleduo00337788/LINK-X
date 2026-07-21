package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.CreateGroupAssetDTO;
import com.linkx.server.controller.vo.GroupAssetVO;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.SysUser;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.FileStorageService;
import com.linkx.server.service.GroupAssetService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupAssetServiceImpl implements GroupAssetService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final GroupAssetMapper groupAssetMapper;
    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final SysUserMapper sysUserMapper;
    private final FileStorageService fileStorageService;
    private final MediaUrlService mediaUrlService;

    @Override
    public List<GroupAssetVO> list(Long userId, Long conversationId, String type) {
        assertGroupMember(userId, conversationId);
        QueryWrapper qw = QueryWrapper.create()
                .where(GroupAsset::getConversationId).eq(conversationId)
                .orderBy(GroupAsset::getCreateTime, false);
        if (StringUtils.hasText(type)) {
            qw.and(GroupAsset::getType).eq(normalizeType(type));
        }
        List<GroupAsset> assets = groupAssetMapper.selectListByQuery(qw);
        Map<Long, SysUser> users = loadUsers(assets.stream().map(GroupAsset::getUploaderId).collect(Collectors.toSet()));
        return assets.stream().map(a -> toVO(a, users.get(a.getUploaderId()))).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GroupAssetVO create(Long userId, Long conversationId, CreateGroupAssetDTO dto) {
        assertGroupMember(userId, conversationId);
        String type = normalizeType(dto.getType());
        if (GroupAsset.TYPE_ESSENCE.equals(type)) {
            assertGroupAdmin(userId, conversationId);
            if (!StringUtils.hasText(dto.getContent()) && !StringUtils.hasText(dto.getTitle())) {
                throw new CustomException(400, "精华内容不能为空");
            }
        } else if (!StringUtils.hasText(dto.getFileKey())) {
            throw new CustomException(400, "请先上传文件");
        }

        GroupAsset asset = GroupAsset.builder()
                .conversationId(conversationId)
                .uploaderId(userId)
                .type(type)
                .title(dto.getTitle())
                .content(dto.getContent())
                .fileName(dto.getFileName())
                .fileSize(dto.getFileSize())
                .fileKey(dto.getFileKey())
                .messageId(dto.getMessageId())
                .downloadCount(0)
                .build();
        groupAssetMapper.insert(asset);
        SysUser uploader = sysUserMapper.selectOneById(userId);
        return toVO(asset, uploader);
    }

    @Override
    @Transactional
    public GroupAssetVO upload(Long userId, Long conversationId, String type, MultipartFile file, String album) {
        assertGroupMember(userId, conversationId);
        String assetType = normalizeType(type);
        if (!GroupAsset.TYPE_FILE.equals(assetType) && !GroupAsset.TYPE_IMAGE.equals(assetType)) {
            throw new CustomException(400, "仅支持上传 file 或 image");
        }
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "文件不能为空");
        }
        if (GroupAsset.TYPE_IMAGE.equals(assetType)) {
            String ct = file.getContentType();
            if (ct == null || !ct.startsWith("image/")) {
                throw new CustomException(400, "相册仅支持图片文件");
            }
        }
        String albumName = StringUtils.hasText(album) ? album.trim() : "默认相册";
        if (albumName.length() > 64) {
            albumName = albumName.substring(0, 64);
        }
        try {
            String objectKey = fileStorageService.uploadFile(file, null);
            GroupAsset asset = GroupAsset.builder()
                    .conversationId(conversationId)
                    .uploaderId(userId)
                    .type(assetType)
                    .title(albumName)
                    .content(albumName)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .fileKey(objectKey)
                    .downloadCount(0)
                    .build();
            groupAssetMapper.insert(asset);
            return toVO(asset, sysUserMapper.selectOneById(userId));
        } catch (RuntimeException e) {
            throw new CustomException(400, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long conversationId, Long assetId) {
        assertGroupMember(userId, conversationId);
        GroupAsset asset = groupAssetMapper.selectOneById(assetId);
        if (asset == null || !Objects.equals(asset.getConversationId(), conversationId)) {
            throw new CustomException(404, "资源不存在");
        }
        ImConversation group = conversationMapper.selectOneById(conversationId);
        boolean isOwner = group != null && Objects.equals(group.getOwnerId(), userId);
        boolean isUploader = Objects.equals(asset.getUploaderId(), userId);
        if (!isOwner && !isUploader) {
            ImConversationMember member = memberMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(ImConversationMember::getConversationId).eq(conversationId)
                            .and(ImConversationMember::getUserId).eq(userId)
            );
            boolean isAdmin = member != null && ImConversationMember.ROLE_ADMIN.equals(member.getRole());
            if (!isAdmin) {
                throw new CustomException(403, "无权删除该资源");
            }
        }
        groupAssetMapper.deleteById(assetId);
    }

    private void assertGroupMember(Long userId, Long conversationId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null) {
            throw new CustomException(403, "你不是该群成员");
        }
    }

    /** 群主或管理员方可操作（设精华等） */
    private void assertGroupAdmin(Long userId, Long conversationId) {
        ImConversation group = conversationMapper.selectOneById(conversationId);
        if (group == null || group.getType() != ImConversation.TYPE_GROUP) {
            throw new CustomException(404, "群聊不存在");
        }
        if (Objects.equals(group.getOwnerId(), userId)) {
            return;
        }
        ImConversationMember member = memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).eq(conversationId)
                        .and(ImConversationMember::getUserId).eq(userId)
        );
        if (member == null || !ImConversationMember.ROLE_ADMIN.equals(member.getRole())) {
            throw new CustomException(403, "只有群主或管理员才能设置精华");
        }
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new CustomException(400, "type 不能为空");
        }
        return switch (type.trim().toLowerCase()) {
            case GroupAsset.TYPE_FILE, GroupAsset.TYPE_IMAGE, GroupAsset.TYPE_ESSENCE -> type.trim().toLowerCase();
            default -> throw new CustomException(400, "type 必须为 file/image/essence");
        };
    }

    private Map<Long, SysUser> loadUsers(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectListByQuery(QueryWrapper.create().where(SysUser::getId).in(ids))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    private GroupAssetVO toVO(GroupAsset asset, SysUser uploader) {
        return GroupAssetVO.builder()
                .id(asset.getId())
                .conversationId(asset.getConversationId())
                .type(asset.getType())
                .title(asset.getTitle())
                .content(asset.getContent())
                .fileName(asset.getFileName())
                .fileSize(asset.getFileSize())
                .fileUrl(mediaUrlService.resolve(asset.getFileKey()))
                .downloadCount(asset.getDownloadCount() == null ? 0 : asset.getDownloadCount())
                .messageId(asset.getMessageId())
                .uploaderId(asset.getUploaderId())
                .uploaderNickname(uploader != null ? uploader.getNickname() : null)
                .createTime(asset.getCreateTime() == null ? null : TIME_FMT.format(asset.getCreateTime().toInstant()))
                .build();
    }
}
