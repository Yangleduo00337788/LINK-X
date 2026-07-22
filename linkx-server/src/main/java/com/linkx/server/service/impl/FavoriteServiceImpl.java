package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SaveFavoriteDTO;
import com.linkx.server.controller.dto.SaveFavoriteTagDTO;
import com.linkx.server.controller.vo.FavoriteStorageVO;
import com.linkx.server.controller.vo.FavoriteTagVO;
import com.linkx.server.controller.vo.FavoriteVO;
import com.linkx.server.entity.Favorite;
import com.linkx.server.entity.FavoriteStorage;
import com.linkx.server.entity.FavoriteTag;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.mapper.FavoriteStorageMapper;
import com.linkx.server.mapper.FavoriteTagMapper;
import com.linkx.server.service.FavoriteService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final String[][] PRESET_TAGS = {
            {"工作", "#f43f5e"},
            {"学习", "#3b82f6"},
            {"生活", "#22c55e"},
            {"灵感", "#f59e0b"},
            {"重要", "#a855f7"}
    };

    private final FavoriteMapper favoriteMapper;
    private final FavoriteStorageMapper favoriteStorageMapper;
    private final FavoriteTagMapper favoriteTagMapper;

    @Override
    public List<FavoriteVO> list(Long userId) {
        ensureStorage(userId);
        ensurePresetTags(userId);
        List<Favorite> list = favoriteMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(Favorite::getUserId).eq(userId)
                        .orderBy(Favorite::getUpdateTime, false)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public FavoriteVO get(Long userId, Long favoriteId) {
        return toVO(requireOwned(userId, favoriteId));
    }

    @Override
    @Transactional
    public FavoriteVO create(Long userId, SaveFavoriteDTO dto) {
        if (!StringUtils.hasText(dto.getContent())) {
            throw new CustomException(400, "收藏内容不能为空");
        }
        FavoriteStorage storage = refreshStorageStats(userId);
        long addSize = dto.getFileSize() != null ? Math.max(0, dto.getFileSize()) : 0L;
        ensureFavoriteCapacity(storage, addSize);

        Favorite fav = Favorite.builder()
                .userId(userId)
                .title(dto.getTitle())
                .content(dto.getContent())
                .type(normalizeType(dto.getType()))
                .sourceType(dto.getSourceType())
                .sourceId(dto.getSourceId())
                .tags(normalizeTags(dto.getTags()))
                .fileSize(dto.getFileSize())
                .build();
        favoriteMapper.insert(fav);
        refreshStorageStats(userId);
        return toVO(fav);
    }

    @Override
    @Transactional
    public FavoriteVO update(Long userId, Long favoriteId, SaveFavoriteDTO dto) {
        Favorite fav = requireOwned(userId, favoriteId);
        Long oldSize = fav.getFileSize() != null ? fav.getFileSize() : 0L;
        if (dto.getTitle() != null) {
            fav.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            fav.setContent(dto.getContent());
        }
        if (StringUtils.hasText(dto.getType())) {
            fav.setType(normalizeType(dto.getType()));
        }
        if (dto.getSourceType() != null) {
            fav.setSourceType(dto.getSourceType());
        }
        if (dto.getSourceId() != null) {
            fav.setSourceId(dto.getSourceId());
        }
        if (dto.getTags() != null) {
            fav.setTags(normalizeTags(dto.getTags()));
        }
        if (dto.getFileSize() != null) {
            fav.setFileSize(dto.getFileSize());
        }
        favoriteMapper.update(fav);

        Long newSize = fav.getFileSize() != null ? fav.getFileSize() : 0L;
        if (!Objects.equals(oldSize, newSize)) {
            FavoriteStorage storage = refreshStorageStats(userId);
            ensureFavoriteCapacity(storage, 0L);
        } else {
            refreshStorageStats(userId);
        }
        return toVO(fav);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long favoriteId) {
        requireOwned(userId, favoriteId);
        favoriteMapper.deleteById(favoriteId);
        refreshStorageStats(userId);
    }

    @Override
    public FavoriteStorageVO getStorage(Long userId) {
        FavoriteStorage storage = refreshStorageStats(userId);
        Map<String, Integer> typeCounts = countByType(userId);
        long used = storage.getUsedBytes() != null ? storage.getUsedBytes() : 0L;
        long quota = storage.getQuotaBytes() != null ? storage.getQuotaBytes() : FavoriteStorage.DEFAULT_QUOTA_BYTES;
        double percent = quota <= 0 ? 0 : Math.min(100.0, used * 100.0 / quota);
        return FavoriteStorageVO.builder()
                .usedBytes(used)
                .quotaBytes(quota)
                .itemCount(storage.getItemCount() != null ? storage.getItemCount() : 0)
                .usedPercent(Math.round(percent * 10) / 10.0)
                .typeCounts(typeCounts)
                .build();
    }

    @Override
    public List<FavoriteTagVO> listTags(Long userId) {
        ensurePresetTags(userId);
        List<FavoriteTag> tags = favoriteTagMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FavoriteTag::getUserId).eq(userId)
                        .orderBy(FavoriteTag::getSortOrder, true)
                        .orderBy(FavoriteTag::getCreateTime, true)
        );
        Map<String, Integer> usage = countTagUsage(userId);
        List<FavoriteTagVO> result = new ArrayList<>();
        for (FavoriteTag tag : tags) {
            result.add(FavoriteTagVO.builder()
                    .id(tag.getId())
                    .name(tag.getName())
                    .color(tag.getColor())
                    .sortOrder(tag.getSortOrder())
                    .preset(tag.getPreset() != null && tag.getPreset() == 1)
                    .count(usage.getOrDefault(tag.getName(), 0))
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public FavoriteTagVO createTag(Long userId, SaveFavoriteTagDTO dto) {
        ensurePresetTags(userId);
        String name = dto.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new CustomException(400, "标签名不能为空");
        }
        long exists = favoriteTagMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(FavoriteTag::getUserId).eq(userId)
                        .and(FavoriteTag::getName).eq(name)
        );
        if (exists > 0) {
            throw new CustomException(400, "标签已存在");
        }
        int maxOrder = favoriteTagMapper.selectListByQuery(
                        QueryWrapper.create().where(FavoriteTag::getUserId).eq(userId)
                ).stream()
                .map(FavoriteTag::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        FavoriteTag tag = FavoriteTag.builder()
                .userId(userId)
                .name(name)
                .color(StringUtils.hasText(dto.getColor()) ? dto.getColor().trim() : "#94a3b8")
                .sortOrder(maxOrder + 1)
                .preset(0)
                .build();
        favoriteTagMapper.insert(tag);
        return FavoriteTagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .sortOrder(tag.getSortOrder())
                .preset(false)
                .count(0)
                .build();
    }

    @Override
    @Transactional
    public void deleteTag(Long userId, Long tagId) {
        FavoriteTag tag = favoriteTagMapper.selectOneById(tagId);
        if (tag == null || !Objects.equals(tag.getUserId(), userId)) {
            throw new CustomException(404, "标签不存在");
        }
        if (tag.getPreset() != null && tag.getPreset() == 1) {
            throw new CustomException(400, "系统预设标签不可删除");
        }
        favoriteTagMapper.deleteById(tagId);
    }

    private Favorite requireOwned(Long userId, Long favoriteId) {
        Favorite fav = favoriteMapper.selectOneById(favoriteId);
        if (fav == null) {
            throw new CustomException(404, "收藏不存在");
        }
        if (!fav.getUserId().equals(userId)) {
            throw new CustomException(403, "无权操作该收藏");
        }
        return fav;
    }

    private FavoriteStorage ensureStorage(Long userId) {
        FavoriteStorage storage = favoriteStorageMapper.selectOneById(userId);
        if (storage != null) return storage;
        storage = FavoriteStorage.builder()
                .userId(userId)
                .quotaBytes(FavoriteStorage.DEFAULT_QUOTA_BYTES)
                .usedBytes(0L)
                .itemCount(0)
                .version(0)
                .build();
        try {
            favoriteStorageMapper.insert(storage);
        } catch (Exception ignored) {
            storage = favoriteStorageMapper.selectOneById(userId);
        }
        return storage != null ? storage : FavoriteStorage.builder()
                .userId(userId)
                .quotaBytes(FavoriteStorage.DEFAULT_QUOTA_BYTES)
                .usedBytes(0L)
                .itemCount(0)
                .version(0)
                .build();
    }

    /** 按收藏表汇总校正 used/itemCount */
    private FavoriteStorage refreshStorageStats(Long userId) {
        FavoriteStorage storage = ensureStorage(userId);
        List<Favorite> all = favoriteMapper.selectListByQuery(
                QueryWrapper.create().where(Favorite::getUserId).eq(userId)
        );
        long used = 0L;
        for (Favorite f : all) {
            if (f.getFileSize() != null && f.getFileSize() > 0) {
                used += f.getFileSize();
            }
        }
        storage.setUsedBytes(used);
        storage.setItemCount(all.size());
        storage.setVersion((storage.getVersion() != null ? storage.getVersion() : 0) + 1);
        favoriteStorageMapper.update(storage);
        return storage;
    }

    /**
     * 当前用量 + 新增字节若超出配额，按 10GiB 步长自动扩容，直到够用或达 60GiB 上限。
     */
    private void ensureFavoriteCapacity(FavoriteStorage storage, long additionalBytes) {
        long used = storage.getUsedBytes() != null ? storage.getUsedBytes() : 0L;
        long quota = storage.getQuotaBytes() != null ? storage.getQuotaBytes() : FavoriteStorage.DEFAULT_QUOTA_BYTES;
        long need = used + Math.max(0L, additionalBytes);
        if (need <= quota) {
            return;
        }
        long next = quota;
        while (next < need && next + FavoriteStorage.EXPAND_STEP_BYTES <= FavoriteStorage.MAX_QUOTA_BYTES) {
            next += FavoriteStorage.EXPAND_STEP_BYTES;
        }
        if (need > next) {
            throw new CustomException(400, "已达最大收藏空间上限（60 GB）");
        }
        storage.setQuotaBytes(next);
        storage.setVersion((storage.getVersion() != null ? storage.getVersion() : 0) + 1);
        favoriteStorageMapper.update(storage);
    }

    private void ensurePresetTags(Long userId) {
        long count = favoriteTagMapper.selectCountByQuery(
                QueryWrapper.create().where(FavoriteTag::getUserId).eq(userId)
        );
        if (count > 0) return;
        int order = 0;
        for (String[] preset : PRESET_TAGS) {
            try {
                favoriteTagMapper.insert(FavoriteTag.builder()
                        .userId(userId)
                        .name(preset[0])
                        .color(preset[1])
                        .sortOrder(order++)
                        .preset(1)
                        .build());
            } catch (Exception ignored) {
                // 并发下忽略
            }
        }
    }

    private Map<String, Integer> countByType(Long userId) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("all", 0);
        map.put("link", 0);
        map.put("image", 0);
        map.put("file", 0);
        map.put("note", 0);
        map.put("message", 0);
        map.put("other", 0);
        List<Favorite> all = favoriteMapper.selectListByQuery(
                QueryWrapper.create().where(Favorite::getUserId).eq(userId)
        );
        map.put("all", all.size());
        for (Favorite f : all) {
            String type = classifyType(f.getType());
            if (map.containsKey(type) && !"all".equals(type)) {
                map.put(type, map.get(type) + 1);
            } else {
                map.put("other", map.get("other") + 1);
            }
        }
        return map;
    }

    private Map<String, Integer> countTagUsage(Long userId) {
        Map<String, Integer> usage = new HashMap<>();
        List<Favorite> all = favoriteMapper.selectListByQuery(
                QueryWrapper.create().where(Favorite::getUserId).eq(userId)
        );
        for (Favorite f : all) {
            for (String tag : parseTagNames(f.getTags())) {
                usage.put(tag, usage.getOrDefault(tag, 0) + 1);
            }
        }
        return usage;
    }

    private List<String> parseTagNames(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        String t = raw.trim();
        try {
            if (t.startsWith("[")) {
                // 简易 JSON 数组解析：去掉括号引号
                String inner = t.substring(1, t.length() - 1).trim();
                if (inner.isEmpty()) return List.of();
                List<String> out = new ArrayList<>();
                for (String part : inner.split(",")) {
                    String s = part.trim().replaceAll("^\"|\"$", "").trim();
                    if (!s.isEmpty()) out.add(s);
                }
                return out;
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        List<String> out = new ArrayList<>();
        for (String part : t.split("[,，]")) {
            String s = part.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "note";
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "note", "image", "link", "file", "message" -> type.trim().toLowerCase(Locale.ROOT);
            default -> "note";
        };
    }

    private String classifyType(String type) {
        if (!StringUtils.hasText(type)) return "note";
        String t = type.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "note", "image", "link", "file", "message" -> t;
            default -> "other";
        };
    }

    private String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return null;
        }
        String t = tags.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private FavoriteVO toVO(Favorite fav) {
        return FavoriteVO.builder()
                .id(fav.getId())
                .title(fav.getTitle())
                .content(fav.getContent())
                .type(fav.getType())
                .sourceType(fav.getSourceType())
                .sourceId(fav.getSourceId())
                .tags(fav.getTags())
                .fileSize(fav.getFileSize())
                .createTime(fav.getCreateTime() == null ? null : TIME_FMT.format(fav.getCreateTime().toInstant()))
                .updateTime(fav.getUpdateTime() == null ? null : TIME_FMT.format(fav.getUpdateTime().toInstant()))
                .build();
    }
}
