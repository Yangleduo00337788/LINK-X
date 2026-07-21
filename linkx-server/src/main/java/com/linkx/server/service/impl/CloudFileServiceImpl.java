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
                    .fileSize(msg.getFileSize())
                    .fileUrl(mediaUrlService.resolve(msg.getFileUrl()))
                    .category(cat)
                    .conversationId(msg.getConversationId())
                    .conversationName(resolveConvName(conv, userId, users))
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
                    .fileSize(asset.getFileSize())
                    .fileUrl(mediaUrlService.resolve(asset.getFileKey()))
                    .category(cat)
                    .conversationId(asset.getConversationId())
                    .conversationName(conv != null ? conv.getName() : null)
                    .senderName(uploader != null ? uploader.getNickname() : null)
                    .createTime(asset.getCreateTime() == null ? null : asset.getCreateTime().getTime())
                    .build());
        }

        return result.stream()
                .sorted(Comparator.comparing(CloudFileVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap)
                .collect(Collectors.toList());
    }

    private String resolveConvName(ImConversation conv, Long userId, Map<Long, SysUser> users) {
        if (conv == null) {
            return null;
        }
        if (Objects.equals(conv.getType(), ImConversation.TYPE_GROUP)) {
            return conv.getName();
        }
        return conv.getName();
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
