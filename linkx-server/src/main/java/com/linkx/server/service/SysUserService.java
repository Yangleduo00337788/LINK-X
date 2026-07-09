// 业务服务接口包
package com.linkx.server.service;

// 登录请求 DTO
import com.linkx.server.controller.dto.LoginDTO;
// 注册请求 DTO
import com.linkx.server.controller.dto.RegisterDTO;
// 登录响应 VO
import com.linkx.server.controller.vo.TokenVO;
// 用户实体
import com.linkx.server.entity.SysUser;
// MyBatis-Flex 通用 Service 接口，提供 save/queryChain 等
import com.mybatisflex.core.service.IService;

/**
 * 系统用户业务服务接口。
 * <p>
 * 继承 IService 获得 MyBatis-Flex 内置 CRUD 能力，
 * 并扩展注册、登录两个认证相关业务方法。
 * </p>
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户注册：校验用户名唯一性，BCrypt 加密密码后入库。
     *
     * @param registerDTO 前端提交的注册信息
     */
    void register(RegisterDTO registerDTO);

    /**
     * 用户登录：校验账号密码与状态，生成双 Token 并返回用户信息。
     *
     * @param loginDTO 前端提交的登录信息
     * @return 包含 accessToken、refreshToken、user 的 TokenVO
     */
    TokenVO login(LoginDTO loginDTO);
}
