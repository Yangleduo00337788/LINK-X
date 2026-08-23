package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
public interface StatisticSnapshotService {

    /**
     * 写入昨日统计快照（统计 + 工作台摘要）。
     *
     * @return 写入条数
     */
    int captureYesterdaySnapshots();

    /**
     * 回填最近若干天的统计与热力图快照（不含工作台摘要 JSON）。
     *
     * @param days 回填天数，1–90，截至昨日
     * @return 写入条数
     */
    int backfillSnapshots(int days);
}
