package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysAuditLog;
import com.linkx.server.mapper.SysAuditLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 审计哈希链链尾快照外部锚定（本机只追加稽核日志）。
 * <p>
 * 定时把「当前链尾行 id + log_hash」以一行一条追加到 DB 之外的只追加文件
 * （默认 {@code logs/audit-chain-anchor.log}）。
 * 攻击者即便重算了库内整条链、改掉了 log_hash，也无法追平已外发的外部快照，
 * 校验端点通过比对「锚定 id 处库内重算哈希」与外部快照即可发现篡改。
 * </p>
 * <p>
 * 行格式：{epochMs} {chainTailId} {chainTailHash}
 * 说明：本锚定面向单机自托管；多实例部署建议改用 SnailJob 统一调度 + 对象存储
 * 版本化对象，避免多进程写同一文件竞争。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditChainAnchorService {

    private final SysAuditLogMapper auditLogMapper;
    private final LinkxProperties linkxProperties;

    /** 外部锚定点：链尾所在行 id 及该行的 log_hash。 */
    public record AnchorPoint(long id, String hash) {
    }

    @Scheduled(fixedDelayString = "${linkx.audit.anchor-interval-ms:300000}",
            initialDelayString = "${linkx.audit.anchor-initial-delay-ms:60000}")
    public void snapshot() {
        if (!linkxProperties.getAudit().isAnchorEnabled()) {
            return;
        }
        SysAuditLog tail = findChainTail();
        if (tail == null || tail.getLogHash() == null) {
            return;
        }
        String line = System.currentTimeMillis() + " " + tail.getId() + " " + tail.getLogHash();
        try {
            Path target = resolveAnchorFile();
            Path dir = target.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(target.toFile(),
                    StandardCharsets.UTF_8, true))) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            log.warn("审计链尾快照写盘失败: {}", e.getMessage());
        }
    }

    /** 读取最近一次外部锚定点；无锚定或无链尾时返回 null。 */
    public AnchorPoint readLastAnchorPoint() {
        try {
            Path target = resolveAnchorFile();
            if (!Files.exists(target)) {
                return null;
            }
            String last = null;
            try (BufferedReader reader = new BufferedReader(new FileReader(target.toFile(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        last = line;
                    }
                }
            }
            return last == null ? null : parseLine(last);
        } catch (IOException e) {
            log.warn("读取审计链尾锚定日志失败: {}", e.getMessage());
            return null;
        }
    }

    /** 解析一行锚定记录。 */
    static AnchorPoint parseLine(String line) {
        String[] parts = line == null ? new String[0] : line.trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new AnchorPoint(Long.parseLong(parts[1]), parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SysAuditLog findChainTail() {
        List<SysAuditLog> list = auditLogMapper.selectListByQuery(
                QueryWrapper.create().orderBy(SysAuditLog::getId, false).limit(0, 1));
        return list.isEmpty() ? null : list.get(0);
    }

    private Path resolveAnchorFile() {
        return Paths.get(linkxProperties.getAudit().getAnchorFile());
    }
}