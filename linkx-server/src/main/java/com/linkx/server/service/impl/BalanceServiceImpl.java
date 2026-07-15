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
        UserBalance balance = getOrCreateBalance(userId);

        // 检查余额是否足够
        if (balance.getBalance().compareTo(amount) < 0) {
            throw new CustomException(400, "余额不足");
        }

        BigDecimal before = balance.getBalance();

        // 扣减余额
        balance.setBalance(balance.getBalance().subtract(amount));
        balance.setTotalWithdraw(balance.getTotalWithdraw().add(amount));
        balanceMapper.update(balance);

        // 记录日志（金额存储为负数表示支出）
        logBalanceChange(userId, "deduct", amount, before,
                balance.getBalance(), bizType, bizId, remark, null);
    }

    @Override
    @Transactional
    public void addBalance(Long userId, BigDecimal amount, String bizType, String bizId, String remark) {
        UserBalance balance = getOrCreateBalance(userId);

        BigDecimal before = balance.getBalance();

        // 增加余额
        balance.setBalance(balance.getBalance().add(amount));
        balanceMapper.update(balance);

        // 记录日志
        logBalanceChange(userId, "add", amount, before,
                balance.getBalance(), bizType, bizId, remark, null);
    }

    @Override
    @Transactional
    public void unfreezeAndDeduct(Long userId, BigDecimal amount, String bizId) {
        UserBalance balance = getOrCreateBalance(userId);

        // 从冻结金额中扣减
        if (balance.getFrozen().compareTo(amount) < 0) {
            // 冻结金额不足，从余额扣减剩余部分
            BigDecimal fromFrozen = balance.getFrozen();
            BigDecimal fromBalance = amount.subtract(fromFrozen);

            if (balance.getBalance().compareTo(fromBalance) < 0) {
                throw new CustomException(400, "余额不足");
            }

            balance.setFrozen(BigDecimal.ZERO);
            balance.setBalance(balance.getBalance().subtract(fromBalance));
        } else {
            balance.setFrozen(balance.getFrozen().subtract(amount));
        }

        balanceMapper.update(balance);
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
