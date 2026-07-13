// 视图对象（VO）包
package com.linkx.server.controller.vo;

// Lombok Builder 模式
import lombok.Builder;
// Lombok 数据类
import lombok.Data;

/**
 * 用户基本信息视图对象。
 * <p>
 * 登录成功后嵌套在 TokenVO.user 中返回，不含密码等敏感字段。
 * </p>
 */
@Data // 自动生成 JSON 序列化所需的 getter
@Builder // Service 层使用 builder 组装用户信息
public class UserInfoVO {

    // 用户主键 ID（雪花算法生成）
    private Long id;

    // 登录账号
    private String username;

    // 显示昵称
    private String nickname;

    // 头像 URL，注册时默认使用 DiceBear 随机头像
    private String avatar;

    // 个性签名，可为 null
    private String signature;

    // 性别
    private String gender;

    // 生日毫秒时间戳
    private Long birthday;

    // 国家
    private String country;

    // 省份
    private String province;

    // 地区
    private String region;
}
