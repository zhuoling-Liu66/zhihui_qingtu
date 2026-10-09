package com.example.zhihuiqingtu.common;

import java.nio.charset.StandardCharsets;

/**
 * JWT 签名算法。
 *
 * <p>把"算法名 → 密钥长度要求 → JCA 算法名"这组映射收在一处：
 * 签发端（{@link JwtUtil}）与校验端（{@code config/SecurityConfig} 的 {@code JwtDecoder}）
 * 都从这里取，避免两边各写一份 {@code switch} 而逐渐不一致。
 *
 * <p><b>密钥长度是硬性要求</b>，不是建议值：HMAC 的摘要长度决定了它能安全接受的最短密钥，
 * 密钥过短时库会直接拒绝（JJWT 抛 {@code WeakKeyException}，Nimbus 行为类似）。
 */
public enum JwtAlgorithm {

    /** HMAC-SHA256，密钥至少 256 bit = 32 字节。 */
    HS256("HmacSHA256", 32),

    /** HMAC-SHA384，密钥至少 384 bit = 48 字节。 */
    HS384("HmacSHA384", 48),

    /** HMAC-SHA512，密钥至少 512 bit = 64 字节。 */
    HS512("HmacSHA512", 64);

    private final String jcaName;
    private final int requiredKeyBytes;

    JwtAlgorithm(String jcaName, int requiredKeyBytes) {
        this.jcaName = jcaName;
        this.requiredKeyBytes = requiredKeyBytes;
    }

    /**
     * JCA（Java 密码体系）里的算法名，用于构造 {@code javax.crypto.SecretKey}。
     *
     * @return 例如 {@code HmacSHA512}
     */
    public String jcaName() {
        return jcaName;
    }

    /**
     * 本算法要求的最短密钥长度（字节）。
     *
     * @return 例如 HS512 返回 64
     */
    public int requiredKeyBytes() {
        return requiredKeyBytes;
    }

    /**
     * 按名称解析算法，大小写不敏感。
     *
     * @param name 配置值，例如 {@code HS512}
     * @return 对应算法
     * @throws IllegalStateException 名称不受支持时抛出，让配置错误在启动期暴露（fail-fast）
     */
    public static JwtAlgorithm fromName(String name) {
        if (name != null) {
            for (JwtAlgorithm value : values()) {
                if (value.name().equalsIgnoreCase(name.trim())) {
                    return value;
                }
            }
        }
        throw new IllegalStateException(
                "不支持的 jwt.algorithm=" + name + "，仅支持 HS256 / HS384 / HS512");
    }

    /**
     * 校验给定密钥是否满足本算法的长度要求。
     *
     * @param secret 密钥原文
     * @throws IllegalStateException 密钥过短时抛出
     */
    public void validateKeyLength(String secret) {
        int actual = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (actual < requiredKeyBytes) {
            throw new IllegalStateException(String.format(
                    "jwt.secret 长度不足：%s 要求至少 %d 字节，当前只有 %d 字节。"
                            + "请用 `openssl rand -base64 64` 重新生成，并通过环境变量 JWT_SECRET 注入。",
                    name(), requiredKeyBytes, actual));
        }
    }

    /**
     * 映射为 JJWT 的签名算法常量，供签发端 {@code signWith(key, algorithm)} 使用。
     *
     * <p>枚举常量名与 JJWT 的常量名一一对应，用 {@code valueOf} 转换即可；
     * 若未来 JJWT 改名，编译期就会失败，不会静默退化成别的算法。
     *
     * @return JJWT 算法常量
     */
    public io.jsonwebtoken.SignatureAlgorithm toJjwtAlgorithm() {
        return io.jsonwebtoken.SignatureAlgorithm.valueOf(name());
    }
}
