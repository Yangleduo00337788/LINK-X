package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.admin.dto.AdminMenuDTO;
import com.linkx.server.controller.admin.dto.AdminMenuReorderDTO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminMenuVO;

import java.util.List;

public interface AdminMenuService {

    List<AdminMenuTreeVO> treeAll();

    List<AdminMenuTreeVO> treeForUser(Long userId);

    AdminMenuVO detail(Long id);

    Long create(AdminMenuDTO dto, Long operatorId);

    void update(Long id, AdminMenuDTO dto, Long operatorId);

    void delete(Long id);

    void reorder(AdminMenuReorderDTO dto, Long operatorId);
}
