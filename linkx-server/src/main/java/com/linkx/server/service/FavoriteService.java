package com.linkx.server.service;

import com.linkx.server.controller.dto.SaveFavoriteDTO;
import com.linkx.server.controller.dto.SaveFavoriteTagDTO;
import com.linkx.server.controller.vo.FavoriteStorageVO;
import com.linkx.server.controller.vo.FavoriteTagVO;
import com.linkx.server.controller.vo.FavoriteVO;

import java.util.List;

public interface FavoriteService {

    List<FavoriteVO> list(Long userId);

    FavoriteVO get(Long userId, Long favoriteId);

    FavoriteVO create(Long userId, SaveFavoriteDTO dto);

    FavoriteVO update(Long userId, Long favoriteId, SaveFavoriteDTO dto);

    void delete(Long userId, Long favoriteId);

    FavoriteStorageVO getStorage(Long userId);

    List<FavoriteTagVO> listTags(Long userId);

    FavoriteTagVO createTag(Long userId, SaveFavoriteTagDTO dto);

    void deleteTag(Long userId, Long tagId);
}
