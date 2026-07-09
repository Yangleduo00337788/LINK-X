// 控制器层包：处理 HTTP 请求并返回 JSON 响应
package com.linkx.server.controller;

// 统一响应封装
import com.linkx.server.common.Result;
// 登录请求 DTO
import com.linkx.server.controller.dto.LoginDTO;
// 注册请求 DTO
import com.linkx.server.controller.dto.RegisterDTO;
// 登录成功返回的 Token 视图对象
import com.linkx.server.controller.vo.TokenVO;
// 用户业务服务接口
import com.linkx.server.service.SysUserService;
// Lombok：为 final 字段生成构造器，实现构造器注入
import lombok.RequiredArgsConstructor;
// Spring 校验注解：触发 @NotBlank 等 JSR-380 校验
import org.springframework.validation.annotation.Validated;
// 映射 POST 请求
import org.springframework.web.bind.annotation.PostMapping;
// 将 JSON 请求体反序列化为 Java 对象
import org.springframework.web.bind.annotation.RequestBody;
// 类级别 URL 前缀
import org.springframework.web.bind.annotation.RequestMapping;
// 标记 REST 控制器，返回值直接序列化为 JSON
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证相关接口控制器。
 * <p>
 * 对外暴露注册、登录两个公开接口，路径前缀为 /auth。
 * 实际完整 URL 为 /api/auth/*（context-path 为 /api）。
 * </p>
 */
@RestController // 组合 @Controller + @ResponseBody，返回 JSON
@RequestMapping("/auth") // 所有方法 URL 前缀：/auth
@RequiredArgsConstructor // 通过构造器注入 final 依赖，替代 @Autowired 字段注入
public class AuthController {

    // 用户服务，处理注册与登录业务逻辑
    private final SysUserService sysUserService;

    /**
     * 用户注册接口。
     *
     * @param registerDTO 请求体：username、password、nickname
     * @return 成功时 code=200，data 为 null
     */
    @PostMapping("/register") // POST /api/auth/register
    public Result<Void> register(@Validated @RequestBody RegisterDTO registerDTO) {
        // @Validated 触发 DTO 字段校验；@RequestBody 将 JSON 绑定为 RegisterDTO
        sysUserService.register(registerDTO); // 委托 Service 完成查重、加密、入库
        return Result.success(null);          // 注册成功，无额外返回数据
    }

    /**
     * 用户登录接口。
     *
     * @param loginDTO 请求体：username、password
     * @return 成功时 data 包含 accessToken、refreshToken、user 信息
     */
    @PostMapping("/login") // POST /api/auth/login
    public Result<TokenVO> login(@Validated @RequestBody LoginDTO loginDTO) {
        // 校验账号密码并生成双 Token
        TokenVO tokenVO = sysUserService.login(loginDTO);
        // 将 TokenVO 包装进统一 Result 返回给前端
        return Result.success(tokenVO);
    }
}
