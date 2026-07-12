package com.linkx.server.service;

import com.linkx.server.controller.dto.LoginDTO;
import com.linkx.server.controller.dto.RegisterDTO;
import com.linkx.server.controller.vo.TokenVO;
import com.linkx.server.entity.SysUser;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;

public interface SysUserService extends IService<SysUser> {

    void register(RegisterDTO registerDTO, HttpServletRequest request);

    TokenVO login(LoginDTO loginDTO, String ip, String userAgent, HttpServletRequest request);
}
