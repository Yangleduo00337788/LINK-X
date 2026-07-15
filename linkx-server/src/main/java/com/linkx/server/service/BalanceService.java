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
     * 解冻金额并扣减（红包过期或退款时）
     */
    void unfreezeAndDeduct(Long userId, BigDecimal amount, String bizId);
}
