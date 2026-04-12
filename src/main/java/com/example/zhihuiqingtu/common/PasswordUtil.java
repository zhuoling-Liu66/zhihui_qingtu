package com.example.zhihuiqingtu.common;

import com.example.zhihuiqingtu.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码工具类，当前使用 SHA-256 做基础摘要。
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String sha256(String rawText) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(rawText.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(5001, "密码加密失败");
        }
    }
}
