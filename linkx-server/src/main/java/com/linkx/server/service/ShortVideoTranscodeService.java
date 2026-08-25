package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
public interface ShortVideoTranscodeService {

    /** 处理一批待转码作品，返回成功数量 */
    int processPendingBatch();
}
