package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminBlacklistAddDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistQueryDTO;
import com.linkx.server.controller.admin.dto.AdminBlacklistReleaseDTO;
import com.linkx.server.controller.admin.vo.AdminBlacklistVO;

public interface AdminBlacklistService {

    PageResultVO<AdminBlacklistVO> list(AdminBlacklistQueryDTO query);

    AdminBlacklistVO detail(Long id);

    void add(AdminBlacklistAddDTO dto, Long operatorId);

    void release(Long id, AdminBlacklistReleaseDTO dto, Long operatorId);

    /** 由用户封禁动作同步写入/刷新生效中的黑名单记录 */
    void recordBan(Long userId, String reason, Long operatorId);

    /** 由用户解封动作同步释放生效中的黑名单记录 */
    void releaseByUserId(Long userId, String releaseReason, Long operatorId);

    /** 是否存在生效中的封禁黑名单 */
    boolean hasActiveBan(Long userId);
}
