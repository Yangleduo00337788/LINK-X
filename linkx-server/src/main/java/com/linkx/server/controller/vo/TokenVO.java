// 视图对象（VO）包：封装返回给前端的响应数据
package com.linkx.server.controller.vo;

// Lombok：支持 Builder 模式链式构建对象
import lombok.Builder;
// Lombok：生成 getter/setter
import lombok.Data;

/**
 * 登录成功后的 Token 响应对象。
 * <p>
 * 作为 Result.data 返回给前端，前端存入 localStorage 并在后续请求携带 AccessToken。
 * </p>
 */
@Data // 生成访问器，Jackson 序列化为 JSON 字段
@Builder // 生成建造者，Service 层链式组装返回值
public class TokenVO {

    // 短期访问令牌，放在 Authorization: Bearer 请求头中
    private String accessToken;

    // 长期刷新令牌，用于 AccessToken 过期后换取新令牌（刷新接口待实现）
    private String refreshToken;

    // Token 过期时间戳（毫秒），当前登录逻辑未赋值，预留字段
    private Long expireTime;

    // 当前登录用户的基本信息，避免前端再次请求用户详情接口
    private UserInfoVO user;
}
