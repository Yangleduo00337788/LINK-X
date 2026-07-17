package com.linkx.server.service.impl;

import com.linkx.server.controller.vo.BalanceVO;
import com.linkx.server.entity.BalanceLog;
import com.linkx.server.entity.UserBalance;
import com.linkx.server.exception.CustomException;
import com.linkx.server.mapper.BalanceLogMapper;
import com.linkx.server.mapper.UserBalanceMapper;
import com.linkx.server.service.BalanceService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 余额服务实现
 */
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final UserBalanceMapper balanceMapper;
    private final BalanceLogMapper balanceLogMapper;

    @Override
    public BalanceVO getBalance(Long userId) {
        UserBalance balance = getOrCreateBalance(userId);
        return toBalanceVO(balance);
    }

    @Override
    @Transactional
    public void deductBalance(Long userId, BigDecimal amount, String bizType, String bizId, String remark) {
        // 先获取当前余额用于记录日志
        UserBalance balance = getOrCreateBalance(userId);
        BigDecimal before = balance.getBalance();

        // 原子扣减，校验 affectedRows
        int rows = balanceMapper.deductBalance(userId, amount);
        if (rows == 0) {
            throw new CustomException(400, "余额不足");
        }

        // 记录日志（金额存储为负数表示支出）
        logBalanceChange(userId, "deduct", amount, before,
                balance.getBalance().subtract(amount), bizType, bizId, remark, null);
    }

    @Override
    @Transactional
    public void addBalance(Long userId, BigDecimal amount, String bizType, String bizId, String remark) {
        // 先获取当前余额用于记录日志
        UserBalance balance = getOrCreateBalance(userId);
        BigDecimal before = balance.getBalance();

        // 原子增加
        balanceMapper.addBalance(userId, amount);

        // 记录日志
        logBalanceChange(userId, "add", amount, before,
                balance.getBalance().add(amount), bizType, bizId, remark, null);
    }

    @Override
    @Transactional
    public void freezeBalance(Long userId, BigDecimal amount, String bizId) {
        UserBalance balance = getOrCreateBalance(userId);
        BigDecimal before = balance.getBalance();

        // 原子冻结：扣减可用余额，增加冻结金额
        int rows = balanceMapper.freezeBalance(userId, amount);
        if (rows == 0) {
            throw new CustomException(400, "余额不足，无法冻结");
        }

        // 记录日志
        logBalanceChange(userId, "freeze", amount, before,
                balance.getBalance().subtract(amount), "freeze", bizId, "冻结金额（红包）", null);
    }

    @Override
    @Transactional
    public void unfreezeAndTransfer(Long fromUserId, Long toUserId, BigDecimal amount, String bizId) {
        // 从发送者冻结金额扣减
        int rows = balanceMapper.unfreezeFromUser(fromUserId, amount);
        if (rows == 0) {
            throw new CustomException(400, "红包资金异常，领取失败");
        }

        // 给领取者增加余额
        balanceMapper.creditUser(toUserId, amount);
    }

    @Override
    @Transactional
    public void unfreezeAndDeduct(Long userId, BigDecimal amount, String bizId) {
        // 原子从冻结金额扣减并加回余额（红包过期退款）
        balanceMapper.unfreezeAndCredit(userId, amount);
    }

    /**
     * 获取或创建用户余额记录
     */
    private UserBalance getOrCreateBalance(Long userId) {
        UserBalance balance = balanceMapper.selectOneByQuery(
                QueryWrapper.create().eq("user_id", userId)
        );

        if (balance == null) {
            balance = UserBalance.builder()
                    .userId(userId)
                    .balance(BigDecimal.ZERO)
                    .frozen(BigDecimal.ZERO)
                    .totalRecharge(BigDecimal.ZERO)
                    .totalWithdraw(BigDecimal.ZERO)
                    .build();
            balanceMapper.insert(balance);
        }

        return balance;
    }

    /**
     * 记录余额变动日志
     */
    private void logBalanceChange(Long userId, String type, BigDecimal amount,
                                   BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   String bizType, String bizId, String remark, Long operatorId) {
        BalanceLog log = BalanceLog.builder()
                .userId(userId)
                .type(type)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .bizType(bizType)
                .bizId(bizId)
                .remark(remark)
                .operatorId(operatorId)
                .build();
        balanceLogMapper.insert(log);
    }

    private BalanceVO toBalanceVO(UserBalance balance) {
        return BalanceVO.builder()
                .userId(balance.getUserId())
                .balance(balance.getBalance())
                .frozen(balance.getFrozen())
                .available(balance.getBalance().subtract(balance.getFrozen()).setScale(2, RoundingMode.DOWN))
                .totalRecharge(balance.getTotalRecharge())
                .totalWithdraw(balance.getTotalWithdraw())
                .build();
    }
}
