package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobBatchVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobLogVO;
import com.linkx.server.controller.admin.vo.AdminSnailJobOverviewVO;

public interface AdminSnailJobMonitorService {

    AdminSnailJobOverviewVO overview();

    PageResultVO<AdminSnailJobBatchVO> listBatches(Long jobId, long page, long size);

    PageResultVO<AdminSnailJobLogVO> listLogs(Long batchId, long page, long size);
}
