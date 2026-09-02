package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.SysAuditLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计日志哈希链：验证链路自洽、篡改可探测、字段口径稳定。
 */
class AuditLogHashChainTest {

    private static SysAuditLog row(long id, String op, String desc, Long userId, String extra) {
        return SysAuditLog.builder()
                .id(id)
                .operationType(op)
                .description(desc)
                .userId(userId)
                .username("user" + userId)
                .status("SUCCESS")
                .extraData(extra)
                .createTime(new Date(1_700_000_000_000L))
                .build();
    }

    /** 模拟写入端：按序构建一条哈希链，返回已填 log_hash/prev_hash 的行。 */
    private static List<SysAuditLog> buildChain(int n) {
        List<SysAuditLog> rows = new ArrayList<>();
        String prevHash = AuditLogHashChain.GENESIS;
        for (int i = 1; i <= n; i++) {
            SysAuditLog r = row(i, "LOGIN", "desc" + i, (long) i, "e" + i);
            r.setPrevHash(prevHash);
            r.setLogHash(AuditLogHashChain.hashFields(r, prevHash));
            prevHash = r.getLogHash();
            rows.add(r);
        }
        return rows;
    }

    /** 模拟校验端：返回可验证完整（自种子起一致）的行数。 */
    private static long verifyIntact(List<SysAuditLog> rows) {
        long intact = 0;
        String expectedPrev = AuditLogHashChain.GENESIS;
        for (SysAuditLog row : rows) {
            if (row.getLogHash() == null || row.getPrevHash() == null
                    || !expectedPrev.equals(row.getPrevHash())
                    || !AuditLogHashChain.hashFields(row, expectedPrev).equals(row.getLogHash())) {
                expectedPrev = AuditLogHashChain.GENESIS;
            } else {
                intact++;
                expectedPrev = row.getLogHash();
            }
        }
        return intact;
    }

    @Test
    void intactChain_verifies_all_rows() {
        List<SysAuditLog> rows = buildChain(5);
        assertEquals(5, verifyIntact(rows));
    }

    @Test
    void tamperedField_detected() {
        List<SysAuditLog> rows = buildChain(5);
        // 篡改第 3 条的描述
        rows.get(2).setDescription("desc-tampered");
        assertEquals(2, verifyIntact(rows));
    }

    @Test
    void tamperedChainBreaks_downstream() {
        List<SysAuditLog> rows = buildChain(5);
        rows.get(2).setDescription("tampered");
        // 前两行完整，第 3 行起受影响（含 3/4/5）
        assertEquals(2, verifyIntact(rows));
        assertFalse(verifyIntact(rows) == 5);
    }

    @Test
    void hashDeterministic_givenSameInput() {
        SysAuditLog a = row(9, "LOGIN", "x", 3L, "y");
        SysAuditLog b = row(9, "LOGIN", "x", 3L, "y");
        a.setPrevHash(AuditLogHashChain.GENESIS);
        b.setPrevHash(AuditLogHashChain.GENESIS);
        assertEquals(AuditLogHashChain.hash(a), AuditLogHashChain.hash(b));
    }

    @Test
    void differentPrevHash_givesDifferentHash() {
        SysAuditLog a = row(1, "LOGIN", "x", 3L, null);
        a.setPrevHash(AuditLogHashChain.GENESIS);
        SysAuditLog b = row(1, "LOGIN", "x", 3L, null);
        b.setPrevHash("0000000000000000000000000000000000000000000000000000000000000001");
        assertFalse(AuditLogHashChain.hash(a).equals(AuditLogHashChain.hash(b)));
    }

    @Test
    void genesisSeeded_firstHashNotNullAndStable() {
        SysAuditLog r = row(1, "LOGIN", "hello", 1L, null);
        r.setPrevHash(AuditLogHashChain.GENESIS);
        assertTrue(AuditLogHashChain.hashFields(r, AuditLogHashChain.GENESIS).length() == 64);
    }
}