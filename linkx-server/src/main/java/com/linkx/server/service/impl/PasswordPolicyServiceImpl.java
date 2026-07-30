package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.PasswordPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    private final LinkxProperties linkxProperties;

    @Override
    public void validate(String password) {
        LinkxProperties.Auth auth = linkxProperties.getAuth();
        int min = auth.getPasswordMinLength();
        int max = auth.getPasswordMaxLength();

        if (!StringUtils.hasText(password)) {
            throw new CustomException(400, "密码不能为空");
        }
        int len = password.length();
        if (len < min || len > max) {
            throw new CustomException(400, "密码长度为 " + min + "-" + max + " 个字符");
        }
        if (auth.isPasswordRequireUpperLower()) {
            if (!password.chars().anyMatch(Character::isUpperCase)
                    || !password.chars().anyMatch(Character::isLowerCase)) {
                throw new CustomException(400, "密码须同时包含大写和小写字母");
            }
        }
        if (auth.isPasswordRequireDigit()) {
            if (!password.chars().anyMatch(Character::isDigit)) {
                throw new CustomException(400, "密码须包含数字");
            }
        }
        if (auth.isPasswordRequireSpecial()) {
            if (password.chars().allMatch(ch -> Character.isLetterOrDigit(ch))) {
                throw new CustomException(400, "密码须包含特殊字符");
            }
        }
    }
}
