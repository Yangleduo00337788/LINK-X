package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminDeptDTO;
import com.linkx.server.controller.admin.vo.AdminDeptVO;

import java.util.List;

public interface AdminDeptService {

    List<AdminDeptVO> tree();

    AdminDeptVO detail(Long id);

    Long create(AdminDeptDTO dto, Long operatorId);

    void update(Long id, AdminDeptDTO dto, Long operatorId);

    void delete(Long id);
}
