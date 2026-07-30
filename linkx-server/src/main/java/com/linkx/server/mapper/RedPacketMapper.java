package com.linkx.server.mapper;

import com.linkx.server.entity.RedPacket;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Mapper
public interface RedPacketMapper extends BaseMapper<RedPacket> {

    @Select("SELECT id, sender_id AS senderId, conversation_id AS conversationId, " +
            "conversation_type AS conversationType, type, total_amount AS totalAmount, " +
            "total_count AS totalCount, remaining_amount AS remainingAmount, " +
            "remaining_count AS remainingCount, greeting, status, " +
            "expire_time AS expireTime, client_msg_id AS clientMsgId, " +
            "create_time AS createTime, version, deleted " +
            "FROM red_packet WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RedPacket selectByIdForUpdate(@Param("id") Long id);

    /**
     * 原子更新红包剩余金额和个数（乐观锁保护）
     * @param id 红包ID
     * @param amount 扣减的金额
     * @param newCount 新的剩余个数
     * @param version 乐观锁版本号
     * @return 更新行数，0 表示乐观锁冲突
     */
    @Update("UPDATE red_packet SET " +
            "remaining_amount = remaining_amount - #{amount}, " +
            "remaining_count = #{newCount}, " +
            "version = version + 1 " +
            "WHERE id = #{id} AND version = #{version} " +
            "AND deleted = 0 AND status = 'active' " +
            "AND remaining_count > 0 " +
            "AND remaining_amount >= #{amount}")
    int updateRemainingAmountAndCount(
            @Param("id") Long id,
            @Param("amount") BigDecimal amount,
            @Param("newCount") Integer newCount,
            @Param("version") Long version
    );

    /**
     * 分批查询过期红包并加行锁（防止 TOCTOU 超退）。
     * 必须在事务中使用，锁在事务提交/回滚后释放。
     * LIMIT 限制单次锁行数，配合定时任务多轮处理积压。
     */
    @Select("SELECT id, sender_id AS senderId, conversation_id AS conversationId, " +
            "conversation_type AS conversationType, type, total_amount AS totalAmount, " +
            "total_count AS totalCount, remaining_amount AS remainingAmount, " +
            "remaining_count AS remainingCount, greeting, status, " +
            "expire_time AS expireTime, client_msg_id AS clientMsgId, " +
            "create_time AS createTime, version, deleted " +
            "FROM red_packet WHERE status = #{status} AND expire_time < #{expireTime} " +
            "AND deleted = 0 ORDER BY id ASC LIMIT #{limit} FOR UPDATE")
    List<RedPacket> selectExpiredForUpdate(
            @Param("status") String status,
            @Param("expireTime") Date expireTime,
            @Param("limit") int limit
    );

    /**
     * 更新红包状态（带乐观锁，防止重复处理）
     * @param id 红包ID
     * @param expectedVersion 期望的版本号
     * @param newStatus 新状态
     * @return 更新行数，0 表示乐观锁冲突或状态已变
     */
    @Update("UPDATE red_packet SET status = #{newStatus}, version = version + 1 " +
            "WHERE id = #{id} AND version = #{expectedVersion} " +
            "AND status = 'active' AND deleted = 0")
    int updateStatusWithVersion(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("newStatus") String newStatus
    );
}
