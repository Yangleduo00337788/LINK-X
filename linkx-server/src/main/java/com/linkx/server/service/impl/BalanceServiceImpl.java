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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * 余额服务实现
 */
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private static final String IDEM_KEY_PREFIX = "linkx:balance:idem:";
    private static final Duration IDEM_KEY_TTL = Duration.ofHours(48);

    private final UserBalanceMapper balanceMapper;
    private final BalanceLogMapper balanceLogMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public BalanceVO getBalance(Long userId) {
        UserBalance balance = getOrCreateBalance(userId);
        return toBalanceVO(balance);
    }

    @Override
    @Transactional
    public void deductBalance(Long userId, BigDecimal amount, String bizType, String bizId, String remark) {
        // 金额必须为正数，防反向充值/扣款
        requirePositiveAmount(amount);
        if (!acquireIdempotency("deduct", userId, bizType, bizId)) {
            return; // 已成功处理，幂等返回
        }

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
        // 金额必须为正数，防反向充值/扣款
        requirePositiveAmount(amount);
        if (!acquireIdempotency("add", userId, bizType, bizId)) {
            return;
        }

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
        // 金额必须为正数，防反向冻结
        requirePositiveAmount(amount);
        if (!acquireIdempotency("freeze", userId, "freeze", bizId)) {
            return;
        }

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
        // 金额必须为正数，防反向转账
        requirePositiveAmount(amount);
        // 领取幂等：同一 bizId 仅成功转出一次（接收方入账与之绑定）
        if (!acquireIdempotency("transfer", fromUserId, "REDPACKET_RECEIVE", bizId)) {
            return;
        }

        // 领取方可能尚无余额行，先确保存在，避免 UPDATE 0 行导致资金从冻结扣走却未入账
        // 同时记录双方变动前余额，用于审计日志
        UserBalance fromBefore = getOrCreateBalance(fromUserId);
        UserBalance toBefore = getOrCreateBalance(toUserId);

        // 从发送者冻结金额扣减
        int rows = balanceMapper.unfreezeFromUser(fromUserId, amount);
        if (rows == 0) {
            throw new CustomException(400, "红包资金异常，领取失败");
        }

        // 给领取者增加余额
        int credited = balanceMapper.creditUser(toUserId, amount);
        if (credited == 0) {
            throw new CustomException(500, "领取入账失败，请稍后重试");
        }

        // 审计日志：发送方冻结转出（balance 不变，仅 frozen 减少，记录解冻动作便于资金追踪）
        logBalanceChange(fromUserId, "unfreeze", amount, fromBefore.getBalance(),
                fromBefore.getBalance(), "REDPACKET_RECEIVE", bizId, "红包领取-冻结转出", null);
        // 审计日志：接收方余额入账
        logBalanceChange(toUserId, "add", amount, toBefore.getBalance(),
                toBefore.getBalance().add(amount), "REDPACKET_RECEIVE", bizId, "红包领取-入账", null);
    }

    @Override
    @Transactional
    public void unfreezeAndDeduct(Long userId, BigDecimal amount, String bizId) {
        // 金额必须为正数，防反向退款
        requirePositiveAmount(amount);
        if (!acquireIdempotency("refund", userId, "REDPACKET_REFUND", bizId)) {
            return;
        }

        // 记录变动前余额，用于审计日志
        UserBalance before = getOrCreateBalance(userId);

        // 原子从冻结金额扣减并加回余额（红包过期退款）；不足则失败，避免静默铸币
        int rows = balanceMapper.unfreezeAndCredit(userId, amount);
        if (rows == 0) {
            throw new CustomException(400, "冻结金额不足，退款失败");
        }

        // 审计日志：红包过期退款，冻结金额加回可用余额
        logBalanceChange(userId, "unfreeze", amount, before.getBalance(),
                before.getBalance().add(amount), "REDPACKET_REFUND", bizId, "红包过期-冻结退款", null);
    }

    /**
     * 校验金额必须为正数；为 null、零或负数时拒绝，防止反向充值/扣款/退款/转账。
     */
    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(400, "金额必须大于 0");
        }
    }

    /**
     * bizId 非空时强制 Redis SETNX 幂等：同一 (op,user,bizType,bizId) 仅成功一次。
     *
     * @return true=首次获得锁，继续执行；false=已成功处理过，调用方应直接返回；
     *         处理中（键在、日志无）抛 409。
     */
    private boolean acquireIdempotency(String op, Long userId, String bizType, String bizId) {
        if (!StringUtils.hasText(bizId)) {
            return true;
        }
        String type = StringUtils.hasText(bizType) ? bizType : "_";
        String idemKey = IDEM_KEY_PREFIX + op + ":" + userId + ":" + type + ":" + bizId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(idemKey, "1", IDEM_KEY_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            long exists = balanceLogMapper.selectCountByQuery(
                    QueryWrapper.create()
                            .where(BalanceLog::getUserId).eq(userId)
                            .and(BalanceLog::getBizId).eq(bizId)
                            .and(BalanceLog::getBizType).eq(type)
            );
            if (exists > 0) {
                return false;
            }
            throw new CustomException(409, "资金操作处理中，请稍后重试");
        }
        registerIdempotencyKeyRollbackCleanup(idemKey);
        return true;
    }

    private void registerIdempotencyKeyRollbackCleanup(String idemKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        redisTemplate.delete(idemKey);
                    } catch (Exception ignored) {
                        // TTL 兜底
                    }
                }
            }
        });
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
            try {
                balanceMapper.insert(balance);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发插入导致 duplicate-key：忽略，重新查询
                balance = balanceMapper.selectOneByQuery(
                        QueryWrapper.create().eq("user_id", userId)
                );
            }
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
                // balance 字段为可用余额（冻结已从 balance 划出），available 与之等同
                .available(balance.getBalance().setScale(2, RoundingMode.DOWN))
                .totalRecharge(balance.getTotalRecharge())
                .totalWithdraw(balance.getTotalWithdraw())
                .build();
    }
}
