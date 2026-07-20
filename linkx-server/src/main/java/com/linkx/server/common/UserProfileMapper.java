package com.linkx.server.common;

import com.linkx.server.controller.vo.UserInfoVO;
import com.linkx.server.controller.vo.UserProfileVO;
import com.linkx.server.entity.SysUser;
import org.springframework.util.StringUtils;

/**
 * 用户资料 VO 构建工具
 */
public final class UserProfileMapper {

    private UserProfileMapper() {
    }

    /** 公开资料（不含邮箱/手机） */
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

    /** 本人私密资料（含脱敏邮箱/手机） */
    public static UserProfileVO toPrivateProfileVO(SysUser user) {
        UserProfileVO vo = toProfileVO(user);
        if (vo == null) {
            return null;
        }
        boolean emailBound = StringUtils.hasText(user.getEmail());
        boolean phoneBound = StringUtils.hasText(user.getPhone());
        vo.setEmailBound(emailBound);
        vo.setPhoneBound(phoneBound);
        vo.setEmail(emailBound ? maskEmail(user.getEmail()) : null);
        vo.setPhone(phoneBound ? maskPhone(user.getPhone()) : null);
        return vo;
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

    public static String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 1) {
            return "*@" + domain;
        }
        if (local.length() == 2) {
            return local.charAt(0) + "*@" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }

    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
