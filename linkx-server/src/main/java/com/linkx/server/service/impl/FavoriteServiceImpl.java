package com.linkx.server.service.impl;

import com.linkx.server.controller.dto.SaveFavoriteDTO;
import com.linkx.server.controller.vo.FavoriteVO;
import com.linkx.server.entity.Favorite;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.FavoriteMapper;
import com.linkx.server.service.FavoriteService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final FavoriteMapper favoriteMapper;

    @Override
    public List<FavoriteVO> list(Long userId) {
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
        Favorite fav = Favorite.builder()
                .userId(userId)
                .title(dto.getTitle())
                .content(dto.getContent())
                .type(normalizeType(dto.getType()))
                .sourceType(dto.getSourceType())
                .sourceId(dto.getSourceId())
                .build();
        favoriteMapper.insert(fav);
        return toVO(fav);
    }

    @Override
    @Transactional
    public FavoriteVO update(Long userId, Long favoriteId, SaveFavoriteDTO dto) {
        Favorite fav = requireOwned(userId, favoriteId);
        if (dto.getTitle() != null) {
            fav.setTitle(dto.getTitle());
        }
        fav.setContent(dto.getContent());
        if (StringUtils.hasText(dto.getType())) {
            fav.setType(normalizeType(dto.getType()));
        }
        if (dto.getSourceType() != null) {
            fav.setSourceType(dto.getSourceType());
        }
        if (dto.getSourceId() != null) {
            fav.setSourceId(dto.getSourceId());
        }
        favoriteMapper.update(fav);
        return toVO(fav);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long favoriteId) {
        requireOwned(userId, favoriteId);
        favoriteMapper.deleteById(favoriteId);
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

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "note";
        }
        return switch (type.trim().toLowerCase()) {
            case "note", "image", "link", "file", "message" -> type.trim().toLowerCase();
            default -> "note";
        };
    }

    private FavoriteVO toVO(Favorite fav) {
        return FavoriteVO.builder()
                .id(fav.getId())
                .title(fav.getTitle())
                .content(fav.getContent())
                .type(fav.getType())
                .sourceType(fav.getSourceType())
                .sourceId(fav.getSourceId())
                .createTime(fav.getCreateTime() == null ? null : TIME_FMT.format(fav.getCreateTime().toInstant()))
                .updateTime(fav.getUpdateTime() == null ? null : TIME_FMT.format(fav.getUpdateTime().toInstant()))
                .build();
    }
}
