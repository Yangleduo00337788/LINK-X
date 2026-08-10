package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminNoticeDTO;
import com.linkx.server.controller.admin.dto.AdminNoticeQueryDTO;
import com.linkx.server.controller.admin.vo.AdminNoticeVO;

public interface AdminNoticeService {

    PageResultVO<AdminNoticeVO> list(AdminNoticeQueryDTO query);

    AdminNoticeVO detail(Long id);

    AdminNoticeVO create(AdminNoticeDTO dto, Long operatorId);

    AdminNoticeVO update(Long id, AdminNoticeDTO dto, Long operatorId);

    void delete(Long id, Long operatorId);

    AdminNoticeVO publish(Long id, Long operatorId);

    AdminNoticeVO unpublish(Long id, Long operatorId);

    /** 管理端通知收件箱：当前仍处于已发布的管理端公告 */
    PageResultVO<AdminNoticeVO> listInbox(AdminNoticeQueryDTO query);
}
