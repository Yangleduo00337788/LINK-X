package com.linkx.server.service.admin;

import com.linkx.server.controller.admin.dto.AdminLoginDTO;
import com.linkx.server.controller.admin.dto.AdminLogoutDTO;
import com.linkx.server.controller.admin.dto.AdminRefreshDTO;
import com.linkx.server.controller.admin.vo.AdminLoginVO;
import com.linkx.server.controller.admin.vo.AdminMenuTreeVO;
import com.linkx.server.controller.admin.vo.AdminUserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Set;

public interface AdminAuthService {

    AdminLoginVO login(AdminLoginDTO dto, HttpServletRequest request, HttpServletResponse response);

    AdminUserProfileVO me(Long userId);

    List<AdminMenuTreeVO> menus(Long userId);

    Set<String> permissions(Long userId);

    void logout(AdminLogoutDTO dto, String authorization, HttpServletRequest request, HttpServletResponse response);

    AdminLoginVO refresh(AdminRefreshDTO dto, HttpServletRequest request, HttpServletResponse response);
}
