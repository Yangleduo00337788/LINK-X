package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群头像拼图用的成员头像预览（已签发 URL）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberAvatarVO {

    private String nickname;

    /** 预签名头像 URL；无头像时为空，前端用昵称首字 */
    private String avatar;
}
