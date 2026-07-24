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
        vo.setEmail(emailBound ? SensitiveDataMasker.maskEmail(user.getEmail()) : null);
        vo.setPhone(phoneBound ? SensitiveDataMasker.maskPhone(user.getPhone()) : null);
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

    /** @deprecated 使用 {@link SensitiveDataMasker#maskEmail(String)} */
    public static String maskEmail(String email) {
        return SensitiveDataMasker.maskEmail(email);
    }

    /** @deprecated 使用 {@link SensitiveDataMasker#maskPhone(String)} */
    public static String maskPhone(String phone) {
        return SensitiveDataMasker.maskPhone(phone);
    }
}
