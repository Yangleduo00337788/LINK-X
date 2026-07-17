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
@Table("red_packet")
public class RedPacket implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 红包类型
     */
    public static final String TYPE_NORMAL = "normal";
    public static final String TYPE_LUCKY = "lucky";

    /**
     * 红包状态
     */
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_FINISHED = "finished";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long senderId;

    private Long conversationId;

    private String type;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 红包个数
     */
    private Integer totalCount;

    /**
     * 剩余金额
     */
    private BigDecimal remainingAmount;

    /**
     * 剩余个数
     */
    private Integer remainingCount;

    /**
     * 祝福语
     */
    private String greeting;

    private String status;

    /**
     * 过期时间
     */
    private Date expireTime;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    /**
     * 乐观锁版本号（手动管理，用于并发控制）
     */
    private Long version;
}
