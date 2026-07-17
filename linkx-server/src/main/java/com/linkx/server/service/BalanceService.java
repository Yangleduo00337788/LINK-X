package com.linkx.server.service;

import com.linkx.server.controller.vo.BalanceVO;

import java.math.BigDecimal;

/**
 * 余额服务接口
 */
public interface BalanceService {

    /**
     * 获取用户余额
     */
    BalanceVO getBalance(Long userId);

    /**
     * 扣减余额（用于发红包等场景）
     */
    void deductBalance(Long userId, BigDecimal amount, String bizType, String bizId, String remark);

    /**
     * 增加余额（用于收红包等场景）
     */
    void addBalance(Long userId, BigDecimal amount, String bizType, String bizId, String remark);

    /**
     * 冻结金额（发送红包时调用，将金额从可用余额转入冻结金额）
     * @param userId 用户ID
     * @param amount 冻结金额
     * @param bizId 业务ID（红包ID）
     */
    void freezeBalance(Long userId, BigDecimal amount, String bizId);

    /**
     * 从冻结金额转出给其他用户（红包领取时调用）
     * @param fromUserId 扣减方
     * @param toUserId 接收方
     * @param amount 金额
     * @param bizId 业务ID
     */
    void unfreezeAndTransfer(Long fromUserId, Long toUserId, BigDecimal amount, String bizId);

    /**
     * 解冻金额并加回可用余额（红包过期退款时调用）
     * @param userId 用户ID
     * @param amount 金额
     * @param bizId 业务ID
     */
    void unfreezeAndDeduct(Long userId, BigDecimal amount, String bizId);
}
