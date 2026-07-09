// 数据传输对象（DTO）包：接收前端请求参数
package com.linkx.server.controller.dto;

// JSR-380 校验：字段不能为空且不能全是空白字符
import jakarta.validation.constraints.NotBlank;
// Lombok：自动生成 getter/setter 等
import lombok.Data;

/**
 * 登录请求参数对象。
 * <p>
 * 对应前端 POST /auth/login 的请求体 JSON。
 * </p>
 */
@Data // 编译期生成访问器，供 Jackson 反序列化与校验框架使用
public class LoginDTO {

    // 登录账号（LinkX ID），不能为空
    @NotBlank(message = "用户名不能为空") // 校验失败时返回该提示信息
    private String username;

    // 登录密码明文，服务端会用 BCrypt 与库中哈希比对
    @NotBlank(message = "密码不能为空")
    private String password;
}
