package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.util.ApiSignUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志哈希链（防篡改）。
 * <p>
 * 约定：本行哈希 = SHA256( 逐字段 SHA256 十六进制串按固定顺序拼接 )，
 * 每个字段先独立 SHA-256 再拼接，天然规避了分隔符（如 '|'、换行）带来的歧义，
 * 后端写入与校验采用同一实现，保证口径一致。
 * </p>
 * <p>
 * 字段顺序（固定，不可随意调整，否则破坏既有链）：
 * id, operationType, description, userId, username, targetUserId, targetUsername,
 * targetResourceId, targetResourceType, ip, userAgent, status, failureReason,
 * extraData, createTime(秒, yyyy-MM-dd HH:mm:ss), prevHash。
 * </p>
 */
public final class AuditLogHashChain {

    /**
     * 默认公开常数锚点：仅当未配置 {@code linkx.audit.hash-chain-seed} 时使用，
     * 能防随机篡改，但无法抵御「取得数据库写权限后重算整链」的强攻击者。
     */
    private static final String DEFAULT_GENESIS = ApiSignUtils.sha256Hex("linkx:audit:chain:v1:genesis");

    /** 当前生效的链首种子（由 configure 在启动时注入，volatile 提供多线程可见性） */
    private static volatile String genesis = DEFAULT_GENESIS;

    private static final DateTimeFormatter CREATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AuditLogHashChain() {
    }

    /**
     * 配置链首熵种子（在审计服务启动时调用一次）。
     * 空值回退公开常数锚点；非空则经域分隔派生，保证同一 seed 下确定、且与常数锚点可区分。
     */
    public static synchronized void configure(String hashChainSeed) {
        if (hashChainSeed == null || hashChainSeed.isBlank()) {
            genesis = DEFAULT_GENESIS;
            return;
        }
        genesis = ApiSignUtils.sha256Hex("linkx:audit:chain:v1:seed:" + hashChainSeed.trim());
    }

    /** 读取当前链首种子（hex）。 */
    public static String genesis() {
        return genesis;
    }

    /**
     * 使用实体自带 prevHash 计算本行哈希（用于读取/展示场景）。
     */
    public static String hash(SysAuditLog row) {
        return hashFields(row, row.getPrevHash());
    }

    /**
     * 使用显式 prevHash（来自前驱行或种子）计算本行哈希。
     */
    public static String hashFields(SysAuditLog row, String prevHash) {
        StringBuilder sb = new StringBuilder(16 * 32);
        sb.append(field(row.getId()));
        sb.append(field(row.getOperationType()));
        sb.append(field(row.getDescription()));
        sb.append(field(row.getUserId()));
        sb.append(field(row.getUsername()));
        sb.append(field(row.getTargetUserId()));
        sb.append(field(row.getTargetUsername()));
        sb.append(field(row.getTargetResourceId()));
        sb.append(field(row.getTargetResourceType()));
        sb.append(field(row.getIp()));
        sb.append(field(row.getUserAgent()));
        sb.append(field(row.getStatus()));
        sb.append(field(row.getFailureReason()));
        sb.append(field(row.getExtraData()));
        sb.append(field(formatCreateTime(row.getCreateTime())));
        sb.append(field(prevHash == null ? "" : prevHash));
        return ApiSignUtils.sha256Hex(sb.toString());
    }

    private static String field(Object value) {
        return ApiSignUtils.sha256Hex(value == null ? "" : String.valueOf(value).trim()
                .replace("\r", "").replace("\n", ""));
    }

    private static String formatCreateTime(java.util.Date date) {
        if (date == null) {
            return "";
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(CREATE_TIME_FMT);
    }
}