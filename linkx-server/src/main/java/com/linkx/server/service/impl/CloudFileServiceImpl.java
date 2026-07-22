package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.CloudFileVO;
import com.linkx.server.entity.GroupAsset;
import com.linkx.server.entity.ImConversation;
import com.linkx.server.entity.ImConversationMember;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.SysUser;
import com.linkx.server.mapper.GroupAssetMapper;
import com.linkx.server.mapper.ImConversationMapper;
import com.linkx.server.mapper.ImConversationMemberMapper;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.mapper.SysUserMapper;
import com.linkx.server.service.CloudFileService;
import com.linkx.server.service.MediaUrlService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CloudFileServiceImpl implements CloudFileService {

    private final ImConversationMemberMapper memberMapper;
    private final ImConversationMapper conversationMapper;
    private final ImMessageMapper messageMapper;
    private final GroupAssetMapper groupAssetMapper;
    private final SysUserMapper sysUserMapper;
    private final MediaUrlService mediaUrlService;

    @Override
    public List<CloudFileVO> listMine(Long userId, String category, int limit) {
        int cap = Math.min(Math.max(limit, 1), 200);
        List<ImConversationMember> memberships = memberMapper.selectListByQuery(
                QueryWrapper.create().where(ImConversationMember::getUserId).eq(userId)
        );
        if (memberships.isEmpty()) {
            return List.of();
        }
        Set<Long> convIds = memberships.stream()
                .map(ImConversationMember::getConversationId)
                .collect(Collectors.toSet());

        Map<Long, ImConversation> convMap = conversationMapper.selectListByQuery(
                        QueryWrapper.create().where(ImConversation::getId).in(convIds))
                .stream()
                .collect(Collectors.toMap(ImConversation::getId, Function.identity(), (a, b) -> a));

        List<ImMessage> messages = messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImMessage::getConversationId).in(convIds)
                        .and(ImMessage::getType).in(ImMessage.TYPE_FILE, ImMessage.TYPE_IMAGE)
                        .orderBy(ImMessage::getCreateTime, false)
                        .limit(cap)
        );

        Set<Long> senderIds = messages.stream().map(ImMessage::getSenderId).collect(Collectors.toSet());
        Map<Long, SysUser> users = senderIds.isEmpty() ? Map.of() :
                sysUserMapper.selectListByQuery(QueryWrapper.create().where(SysUser::getId).in(senderIds))
                        .stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<CloudFileVO> result = new ArrayList<>();
        Map<Long, String> privatePeerNames = resolvePrivatePeerNames(userId, convMap, memberships);

        for (ImMessage msg : messages) {
            ImConversation conv = convMap.get(msg.getConversationId());
            SysUser sender = users.get(msg.getSenderId());
            String cat = categorize(msg.getType(), msg.getFileName());
            if (StringUtils.hasText(category) && !category.equals(cat)) {
                continue;
            }
            result.add(CloudFileVO.builder()
                    .id(msg.getId())
                    .source("chat_message")
                    .title(msg.getFileName() != null ? msg.getFileName() : msg.getContent())
                    .fileName(msg.getFileName())
                    .fileSize(sanitizeFileSize(msg.getFileSize()))
                    .fileUrl(mediaUrlService.resolve(msg.getFileUrl()))
                    .category(cat)
                    .conversationId(msg.getConversationId())
                    .conversationName(resolveConvName(conv, msg.getConversationId(), privatePeerNames))
                    .senderName(sender != null ? sender.getNickname() : null)
                    .createTime(msg.getCreateTime() == null ? null : msg.getCreateTime().getTime())
                    .build());
        }

        List<GroupAsset> assets = groupAssetMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(GroupAsset::getConversationId).in(convIds)
                        .and(GroupAsset::getType).in(GroupAsset.TYPE_FILE, GroupAsset.TYPE_IMAGE)
                        .orderBy(GroupAsset::getCreateTime, false)
                        .limit(cap)
        );
        Set<Long> uploaderIds = assets.stream().map(GroupAsset::getUploaderId).collect(Collectors.toSet());
        Map<Long, SysUser> uploaders = uploaderIds.isEmpty() ? Map.of() :
                sysUserMapper.selectListByQuery(QueryWrapper.create().where(SysUser::getId).in(uploaderIds))
                        .stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        for (GroupAsset asset : assets) {
            ImConversation conv = convMap.get(asset.getConversationId());
            SysUser uploader = uploaders.get(asset.getUploaderId());
            String cat = GroupAsset.TYPE_IMAGE.equals(asset.getType())
                    ? "image"
                    : categorize(ImMessage.TYPE_FILE, asset.getFileName());
            if (StringUtils.hasText(category) && !category.equals(cat)) {
                continue;
            }
            result.add(CloudFileVO.builder()
                    .id(asset.getId())
                    .source("group_asset")
                    .title(asset.getTitle() != null ? asset.getTitle() : asset.getFileName())
                    .fileName(asset.getFileName())
                    .fileSize(sanitizeFileSize(asset.getFileSize()))
                    .fileUrl(mediaUrlService.resolve(asset.getFileKey()))
                    .category(cat)
                    .conversationId(asset.getConversationId())
                    .conversationName(resolveConvName(conv, asset.getConversationId(), privatePeerNames))
                    .senderName(uploader != null ? uploader.getNickname() : null)
                    .createTime(asset.getCreateTime() == null ? null : asset.getCreateTime().getTime())
                    .build());
        }

        return result.stream()
                .sorted(Comparator.comparing(CloudFileVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap)
                .collect(Collectors.toList());
    }

    /** 过滤误写入的异常体积（如雪花 ID） */
    private Long sanitizeFileSize(Long size) {
        if (size == null || size < 0 || size > 50L * 1024 * 1024 * 1024) {
            return 0L;
        }
        return size;
    }

    private Map<Long, String> resolvePrivatePeerNames(
            Long userId,
            Map<Long, ImConversation> convMap,
            List<ImConversationMember> memberships
    ) {
        Set<Long> privateConvIds = convMap.values().stream()
                .filter(c -> Objects.equals(c.getType(), ImConversation.TYPE_PRIVATE))
                .map(ImConversation::getId)
                .collect(Collectors.toSet());
        if (privateConvIds.isEmpty()) {
            return Map.of();
        }
        List<ImConversationMember> peers = memberMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImConversationMember::getConversationId).in(privateConvIds)
                        .and(ImConversationMember::getUserId).ne(userId)
        );
        Set<Long> peerUserIds = peers.stream().map(ImConversationMember::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> peerUsers = peerUserIds.isEmpty() ? Map.of() :
                sysUserMapper.selectListByQuery(QueryWrapper.create().where(SysUser::getId).in(peerUserIds))
                        .stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> result = new java.util.HashMap<>();
        for (ImConversationMember m : peers) {
            SysUser u = peerUsers.get(m.getUserId());
            if (u != null && StringUtils.hasText(u.getNickname())) {
                result.put(m.getConversationId(), u.getNickname());
            } else if (u != null && StringUtils.hasText(u.getUsername())) {
                result.put(m.getConversationId(), u.getUsername());
            }
        }
        return result;
    }

    private String resolveConvName(ImConversation conv, Long conversationId, Map<Long, String> privatePeerNames) {
        if (conv == null) {
            return privatePeerNames.get(conversationId);
        }
        if (Objects.equals(conv.getType(), ImConversation.TYPE_GROUP) && StringUtils.hasText(conv.getName())) {
            return conv.getName();
        }
        if (StringUtils.hasText(conv.getName())) {
            return conv.getName();
        }
        return privatePeerNames.get(conversationId);
    }

    private String categorize(String msgType, String fileName) {
        if (ImMessage.TYPE_IMAGE.equals(msgType)) {
            return "image";
        }
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (name.matches(".*\\.(mp4|mov|avi|mkv|webm)$")) {
            return "media";
        }
        if (name.matches(".*\\.(png|jpe?g|gif|webp|bmp)$")) {
            return "image";
        }
        if (name.matches(".*\\.(doc|docx|pdf|ppt|pptx|xls|xlsx|txt|md)$")) {
            return "document";
        }
        return "other";
    }
}
