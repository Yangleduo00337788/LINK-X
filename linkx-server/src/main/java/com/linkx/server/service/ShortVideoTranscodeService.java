package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
public interface ShortVideoTranscodeService {

    /** 处理一批待转码作品，返回成功数量 */
    int processPendingBatch();

    /** 若作品处于 pending，则立即转码（供上传后异步触发） */
    boolean transcodePostIfPending(Long postId);

    /** 将作品重新加入转码队列 */
    void enqueueRetranscode(Long postId);
}
