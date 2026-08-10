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
}
