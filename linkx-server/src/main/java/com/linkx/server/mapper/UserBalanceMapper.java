package com.linkx.server.mapper;

import com.linkx.server.entity.UserBalance;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserBalanceMapper extends BaseMapper<UserBalance> {

    /**
     * 原子扣减余额（余额不足时返回 0）
     * @param userId 用户ID
     * @param amount 扣减金额
     * @return 更新行数，1 表示成功，0 表示余额不足
     */
    @Update("UPDATE user_balance SET " +
            "balance = balance - #{amount}, " +
            "total_withdraw = total_withdraw + #{amount} " +
            "WHERE user_id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子增加余额
     * @param userId 用户ID
     * @param amount 增加金额
     * @return 更新行数，1 表示成功
     */
    @Update("UPDATE user_balance SET " +
            "balance = balance + #{amount}, " +
            "total_recharge = total_recharge + #{amount} " +
            "WHERE user_id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子冻结金额（从可用余额转入冻结金额）
     * @param userId 用户ID
     * @param amount 冻结金额
     * @return 更新行数，1 表示成功，0 表示余额不足
     */
    @Update("UPDATE user_balance SET " +
            "balance = balance - #{amount}, " +
            "frozen = frozen + #{amount} " +
            "WHERE user_id = #{userId} AND balance >= #{amount}")
    int freezeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子从冻结金额转出并增加对方余额（红包领取时）
     * @param fromUserId 扣减方（发送者）
     * @param toUserId 接收方（领取者）
     * @param amount 金额
     * @return 更新行数，1 表示成功，0 表示冻结金额不足
     */
    @Update("UPDATE user_balance SET " +
            "frozen = frozen - #{amount} " +
            "WHERE user_id = #{fromUserId} AND frozen >= #{amount}")
    int unfreezeFromUser(@Param("fromUserId") Long fromUserId, @Param("amount") BigDecimal amount);

    /**
     * 原子增加目标用户余额（红包领取时）
     * @param toUserId 接收方
     * @param amount 金额
     * @return 更新行数，1 表示成功
     */
    @Update("UPDATE user_balance SET " +
            "balance = balance + #{amount}, " +
            "total_recharge = total_recharge + #{amount} " +
            "WHERE user_id = #{toUserId}")
    int creditUser(@Param("toUserId") Long toUserId, @Param("amount") BigDecimal amount);

    /**
     * 原子从冻结金额扣减并加回余额（红包过期退款）
     * @param userId 用户ID
     * @param amount 退还金额
     * @return 更新行数，1 表示成功
     */
    @Update("UPDATE user_balance SET " +
            "frozen = frozen - #{amount}, " +
            "balance = balance + #{amount} " +
            "WHERE user_id = #{userId} AND frozen >= #{amount}")
    int unfreezeAndCredit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
