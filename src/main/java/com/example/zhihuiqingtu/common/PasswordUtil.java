package com.example.zhihuiqingtu.common;

import com.example.zhihuiqingtu.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码工具类。
 * <p>
 * 当前项目为了简化实现，使用 SHA-256 对密码做摘要处理。
 * 这样数据库中保存的不是明文密码，而是摘要后的字符串。
 * <p>
 * 对初学者的理解提示：
 * “摘要”不是“加密后还能解密回来”，而是“根据原文算出一个固定结果，用于比较是否一致”。
 */
public final class PasswordUtil {

    /**
     * 工具类不应该被创建实例，因此把构造方法设为 private。
     */
    private PasswordUtil() {
    }

    /**
     * 对原始文本执行 SHA-256 摘要。
     *
     * @param rawText 原始文本，通常是用户输入的密码
     * @return 摘要后的 16 进制字符串
     */
    public static String sha256(String rawText) {
        try {
            // 获取 SHA-256 算法实例。
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            // 先把字符串转换成 UTF-8 字节，再计算摘要。
            byte[] bytes = messageDigest.digest(rawText.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                // 每个字节都转成两位十六进制，便于保存和比较。
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            // 理论上 JDK 一定支持 SHA-256，这里是兜底处理。
            throw new BusinessException(5001, "密码加密失败");
        }
    }
}
