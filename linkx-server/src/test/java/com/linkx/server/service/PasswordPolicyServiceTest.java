package com.linkx.server.service;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.exception.CustomException;
import com.linkx.server.service.impl.PasswordPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("密码策略校验")
class PasswordPolicyServiceTest {

    private LinkxProperties props;
    private PasswordPolicyService policy;

    @BeforeEach
    void setUp() {
        props = new LinkxProperties();
        policy = new PasswordPolicyServiceImpl(props);
    }

    @Test
    @DisplayName("默认策略：8-64，须含数字")
    void defaultPolicyAcceptsLetterAndDigit() {
        assertDoesNotThrow(() -> policy.validate("password1"));
    }

    @Test
    @DisplayName("过短应拒绝")
    void tooShortRejected() {
        CustomException ex = assertThrows(CustomException.class, () -> policy.validate("ab1"));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("开启大小写后仅小写应拒绝")
    void requireUpperLower() {
        props.getAuth().setPasswordRequireUpperLower(true);
        assertThrows(CustomException.class, () -> policy.validate("password1"));
        assertDoesNotThrow(() -> policy.validate("Password1"));
    }

    @Test
    @DisplayName("开启特殊字符后纯字母数字应拒绝")
    void requireSpecial() {
        props.getAuth().setPasswordRequireSpecial(true);
        assertThrows(CustomException.class, () -> policy.validate("Password1"));
        assertDoesNotThrow(() -> policy.validate("Password1!"));
    }

    @Test
    @DisplayName("关闭数字要求后纯字母可通过")
    void digitOptional() {
        props.getAuth().setPasswordRequireDigit(false);
        assertDoesNotThrow(() -> policy.validate("password"));
    }
}
