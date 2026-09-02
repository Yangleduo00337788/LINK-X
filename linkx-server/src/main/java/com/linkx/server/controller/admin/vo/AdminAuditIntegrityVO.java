package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 审计日志哈希链完整性校验结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditIntegrityVO {

    /** 审计日志总行数 */
    private long total;

    /** 可验证为完整（自种子起的链上一致）的行数 */
    private long intact;

    /** 被篡改或链断裂受影响的行数 */
    private long tampered;

    /** 整条哈希链是否校验通过 */
    private boolean chainIntact;

    /** 校验时间 */
    private Date verifiedAt;
}