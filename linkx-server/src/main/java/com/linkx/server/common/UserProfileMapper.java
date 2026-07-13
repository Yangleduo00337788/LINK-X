package com.linkx.server.common;

import com.linkx.server.controller.vo.UserInfoVO;
import com.linkx.server.controller.vo.UserProfileVO;
import com.linkx.server.entity.SysUser;

/**
 * 用户资料 VO 构建工具
 */
public final class UserProfileMapper {

    private UserProfileMapper() {
    }

    public static UserProfileVO toProfileVO(SysUser user) {
        if (user == null) {
            return null;
        }
        return UserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .signature(user.getSignature())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .country(user.getCountry())
                .province(user.getProvince())
                .region(user.getRegion())
                .createTime(user.getCreateTime())
                .build();
    }

    public static UserInfoVO toInfoVO(SysUser user) {
        if (user == null) {
            return null;
        }
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .signature(user.getSignature())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .country(user.getCountry())
                .province(user.getProvince())
                .region(user.getRegion())
                .build();
    }
}
