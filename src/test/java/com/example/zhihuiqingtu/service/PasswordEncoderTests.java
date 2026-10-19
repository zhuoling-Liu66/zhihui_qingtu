package com.example.zhihuiqingtu.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码编码器行为测试。
 * <p>
 * 这个测试类同时充当"BCrypt 与裸 SHA-256 到底差在哪"的可执行文档：
 * 每个用例都对应一条前面文档里讲过的性质，跑一遍就能看到结论。
 * <p>
 * 注意这里直接 new 一个 encoder，不启动 Spring 容器 —— 被测对象是纯逻辑，
 * 用 {@code @SpringBootTest} 只会让测试变慢，没有额外收益。
 */
class PasswordEncoderTests {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 性质一：密文能验证出正确的明文。
     * 对应登录成功的情况。
     */
    @Test
    void shouldMatchCorrectPassword() {
        String encoded = passwordEncoder.encode("123456");
        assertTrue(passwordEncoder.matches("123456", encoded));
    }

    /**
     * 性质二：密文不能验证出错误的明文。
     * 对应登录失败的情况。这也是为什么登录接口在密码错时统一提示"用户名或密码错误"，
     * 不区分"用户不存在"和"密码错误"—— 避免向攻击者泄露账号是否存在。
     */
    @Test
    void shouldNotMatchWrongPassword() {
        String encoded = passwordEncoder.encode("123456");
        assertFalse(passwordEncoder.matches("654321", encoded));
    }

    /**
     * 性质三（这是和裸 SHA-256 最大的区别）：同一个密码，每次编码结果都不同。
     * <p>
     * 因为 BCrypt 每次都会生成一个新的随机盐。旧实现 {@code PasswordUtil.sha256("123456")}
     * 每次永远是同一个 64 位十六进制串，正因如此才会被彩虹表查表破解。
     */
    @Test
    void shouldProduceDifferentCipherForSamePassword() {
        assertNotEquals(passwordEncoder.encode("123456"), passwordEncoder.encode("123456"));
    }

    /**
     * 性质四：虽然密文不同，但都能验证通过同一个明文。
     * 这解释了为什么 BCrypt 必须用 matches() 而不是 equals() 比对。
     */
    @Test
    void shouldMatchBothCiphersEncodedFromSamePassword() {
        String first = passwordEncoder.encode("123456");
        String second = passwordEncoder.encode("123456");
        assertNotEquals(first, second);
        assertTrue(passwordEncoder.matches("123456", first));
        assertTrue(passwordEncoder.matches("123456", second));
    }

    /**
     * 性质五：密文是 60 个字符，且以 $2a$ 开头。
     * <p>
     * 结构是 {@code $2a$10$<22 字符盐><31 字符哈希>}，盐和哈希存在同一个字符串里，
     * 所以数据库不需要额外加 salt 列 —— 原来的 {@code password_hash VARCHAR(255)} 长度完全够用。
     */
    @Test
    void shouldProduceSixtyCharCipher() {
        String encoded = passwordEncoder.encode("123456");
        assertEquals(60, encoded.length());
        assertTrue(encoded.startsWith("$2a$"));
    }

    /**
     * 性质六：明文相同但盐不同，两个密文互不相同。
     * 这是"每个用户独立盐"的意义 —— 攻击者破解了用户 A，对用户 B 毫无帮助。
     */
    @Test
    void shouldProduceDifferentCipherForDifferentSalt() {
        String userACipher = passwordEncoder.encode("123456");
        String userBCipher = passwordEncoder.encode("123456");
        assertNotEquals(userACipher, userBCipher);
    }

    /**
     * 性质七：旧的 SHA-256 哈希在换成 BCrypt 后一律校验失败。
     * <p>
     * 这是升级算法必须付出的代价，也是最重要的一个坑：数据库里已存在的用户
     * （password_hash 是 64 位十六进制串）换算法后<b>全部无法登录</b>。
     * 注意这里不会抛异常，matches() 内部发现密文不符合 BCrypt 格式（不是 $2a$ 开头）
     * 时只会打一条 warn 日志然后返回 false —— 表现就是"密码明明是对的，却提示密码错误"，
     * 排查时很容易怀疑错方向。
     */
    @Test
    void sha256CipherShouldFailBcryptMatch() {
        // 下面这串是旧实现 PasswordUtil.sha256("123456") 的输出，可从命令行实测复现：
        //   printf '123456' | sha256sum
        String legacySha256 = "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";
        assertFalse(passwordEncoder.matches("123456", legacySha256));
    }
}
