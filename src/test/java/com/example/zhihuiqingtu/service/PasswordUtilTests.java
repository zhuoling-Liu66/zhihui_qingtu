package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 密码工具测试。
 */
class PasswordUtilTests {

    @Test
    void shouldGenerateStableHashForSamePassword() {
        String rawPassword = "123456";
        assertEquals(PasswordUtil.sha256(rawPassword), PasswordUtil.sha256(rawPassword));
    }

    @Test
    void shouldGenerateDifferentHashForDifferentPassword() {
        assertNotEquals(PasswordUtil.sha256("123456"), PasswordUtil.sha256("654321"));
    }
}
