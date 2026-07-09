// 数据传输对象（DTO）包
package com.linkx.server.controller.dto;

// 非空字符串校验注解
import jakarta.validation.constraints.NotBlank;
// Lombok 数据类注解
import lombok.Data;

/**
 * 注册请求参数对象。
 * <p>
 * 对应前端 POST /auth/register 的请求体 JSON。
 * </p>
 */
@Data // 自动生成 getter/setter，简化 POJO 编写
public class RegisterDTO {

    // 用户注册账号，全局唯一，对应 sys_user.username
    @NotBlank(message = "用户名不能为空")
    private String username;

    // 用户设置的明文密码，入库前会 BCrypt 加密
    @NotBlank(message = "密码不能为空")
    private String password;

    // 用户昵称，展示在聊天界面与个人资料中
    @NotBlank(message = "昵称不能为空")
    private String nickname;
}
