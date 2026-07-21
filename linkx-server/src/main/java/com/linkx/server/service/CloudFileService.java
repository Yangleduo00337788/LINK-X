package com.linkx.server.service;

import com.linkx.server.controller.vo.CloudFileVO;

import java.util.List;

public interface CloudFileService {

    /**
     * 当前用户可访问的云端文件列表（聊天文件消息 + 群文件）
     */
    List<CloudFileVO> listMine(Long userId, String category, int limit);
}
