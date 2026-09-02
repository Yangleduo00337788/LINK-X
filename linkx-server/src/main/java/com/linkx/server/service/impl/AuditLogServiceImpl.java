package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.AuditLogHashChain;
import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.linkx.server.service.AuditLogService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 审计日志服务实现
 * <p>
 * 写入采用哈希链防篡改：每行 log_hash = AuditLogHashChain(字段序列, 前一行 log_hash)，
 * 通过唯一锁对象 serialized 保证并发写入时链顺序一致（与雪花 ID 单调递增对齐）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    /**
     * 串行化哈希链追加的锁：与雪花 ID 递增顺序对齐，避免并发交错破坏链。
     */
    private final Object chainLock = new Object();

    @PostConstruct
    public void backfillChain() {
        try {
            synchronized (chainLock) {
                QueryWrapper qw = QueryWrapper.create();
                qw.where(SysAuditLog::getLogHash).isNull()
                        .or(SysAuditLog::getPrevHash).isNull();
                long gap = auditLogMapper.selectCountByQuery(qw);
                if (gap <= 0) {
                    return;
                }
                List<SysAuditLog> rows = auditLogMapper.selectListByQuery(
                        QueryWrapper.create().orderBy(SysAuditLog::getId, true));
                String prevHash = AuditLogHashChain.GENESIS;
                for (SysAuditLog row : rows) {
                    row.setPrevHash(prevHash);
                    row.setLogHash(AuditLogHashChain.hashFields(row, prevHash));
                    auditLogMapper.update(row);
                    prevHash = row.getLogHash();
                }
                log.info("审计日志哈希链回填完成，共 {} 行", rows.size());
            }
        } catch (Exception e) {
            // 回填失败不影响主流程，后续写入仍按链式追加，可由校验端点发现缺口
            log.warn("审计日志哈希链回填失败: {}", e.getMessage());
        }
    }

    @Override
    @Async("auditExecutor")
    public void log(SysAuditLog.OperationType operationType, String description,
                    Long userId, String username, String ip, String userAgent,
                    boolean success, String reason) {
        SysAuditLog logRow = SysAuditLog.builder()
                .operationType(operationType.name())
                .description(description)
                .userId(userId)
                .username(username)
                .ip(maskIp(ip))
                .userAgent(sanitizeUserAgent(userAgent))
                .status(success ? "SUCCESS" : "FAIL")
                .failureReason(reason)
                .createTime(new Date())
                .build();
        persistChain(logRow);
    }

    @Override
    @Async("auditExecutor")
    public void logWithTarget(SysAuditLog.OperationType operationType, String description,
                              Long userId, String username,
                              Long targetUserId, String targetUsername,
                              String targetResourceId, String targetResourceType,
                              String ip, String userAgent,
                              boolean success, String reason) {
        SysAuditLog logRow = SysAuditLog.builder()
                .operationType(operationType.name())
                .description(description)
                .userId(userId)
                .username(username)
                .targetUserId(targetUserId)
                .targetUsername(targetUsername)
                .targetResourceId(truncate(targetResourceId, 128))
                .targetResourceType(targetResourceType)
                .ip(maskIp(ip))
                .userAgent(sanitizeUserAgent(userAgent))
                .status(success ? "SUCCESS" : "FAIL")
                .failureReason(reason)
                .createTime(new Date())
                .build();
        persistChain(logRow);
    }

    @Override
    @Async("auditExecutor")
    public void logWithExtra(SysAuditLog.OperationType operationType, String description,
                             Long userId, String username,
                             String ip, String userAgent,
                             boolean success, String reason, String extraData) {
        SysAuditLog logRow = SysAuditLog.builder()
                .operationType(operationType.name())
                .description(description)
                .userId(userId)
                .username(username)
                .ip(maskIp(ip))
                .userAgent(sanitizeUserAgent(userAgent))
                .status(success ? "SUCCESS" : "FAIL")
                .failureReason(reason)
                .extraData(truncate(extraData, 2048))
                .createTime(new Date())
                .build();
        persistChain(logRow);
    }

    /**
     * 链式追加写入：在锁内取当前链尾，计算 prevHash 与本行 logHash 后插入。
     */
    private void persistChain(SysAuditLog logRow) {
        if (logRow.getCreateTime() == null) {
            logRow.setCreateTime(new Date());
        }
        synchronized (chainLock) {
            SysAuditLog prev = findChainTail();
            String prevHash = (prev == null || prev.getLogHash() == null)
                    ? AuditLogHashChain.GENESIS : prev.getLogHash();
            logRow.setPrevHash(prevHash);
            auditLogMapper.insert(logRow); // 生成雪花 ID（纳入哈希字段序列）
            logRow.setLogHash(AuditLogHashChain.hashFields(logRow, prevHash));
            auditLogMapper.update(logRow);
        }
    }

    /** 取当前链尾（最大 ID 行），仅需其 logHash。 */
    private SysAuditLog findChainTail() {
        List<SysAuditLog> list = auditLogMapper.selectListByQuery(
                QueryWrapper.create().orderBy(SysAuditLog::getId, false).limit(0, 1));
        return list.isEmpty() ? null : list.get(0);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String sanitized = userAgent.trim();
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    private String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ClientIpResolver.normalizeToIpv4(ip.trim());
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon > 0 && trimmed.indexOf(':') != lastColon) {
            return "[redacted-ipv6]";
        }
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot > 0) {
            return trimmed.substring(0, lastDot + 1) + "*";
        }
        return "[redacted]";
    }
}