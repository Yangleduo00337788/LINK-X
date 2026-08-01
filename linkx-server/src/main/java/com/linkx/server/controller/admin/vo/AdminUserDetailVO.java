package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "用户详情")
public class AdminUserDetailVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String signature;
    private String gender;
    private Long birthday;
    private String country;
    private String province;
    private String region;
    private String email;
    private String phone;
    private Integer status;
    private Long deptId;
    private String deptName;
    /** 是否强制设备绑定 */
    private Boolean deviceBindingEnabled;
    private List<String> roles;
    private List<String> permissions;
    private Date createTime;
    private Date updateTime;
}
