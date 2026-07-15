package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群成员 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberVO {

    private Long userId;

    private String nickname;

    private String avatar;

    private String role;

    private Long joinTime;
}
