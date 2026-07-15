package com.linkx.server.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("balance_log")
public class BalanceLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志类型
     */
    public static final String TYPE_RECHARGE = "recharge";
    public static final String TYPE_SEND_REDPACKET = "send_redpacket";
    public static final String TYPE_RECEIVE_REDPACKET = "receive_redpacket";
    public static final String TYPE_REFUND = "refund";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private String type;

    /**
     * 变动金额（正数）
     */
    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务ID
     */
    private String bizId;

    private String remark;

    /**
     * 操作人ID（管理员充值时）
     */
    private Long operatorId;

    @Column(onInsertValue = "NOW()")
    private Date createTime;
}
