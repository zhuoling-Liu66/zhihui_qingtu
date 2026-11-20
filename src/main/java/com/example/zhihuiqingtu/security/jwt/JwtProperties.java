package com.example.zhihuiqingtu.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import com.example.zhihuiqingtu.config.SecurityConfig;

/**
 * JWT 配置。
 *
 * <p><b>本类存在的意义是"单一事实来源"</b>：签发端（{@link JwtUtil}）与校验端
 * （{@code config/SecurityConfig} 的 {@code JwtDecoder}）必须使用【同一个密钥、同一个算法】，
 * 否则会出现"自己签的 token 自己验不过"，症状是一律 401、极难排查。
 * 把这组配置收在一个类里，两端都依赖它，就不存在"改了一边忘了另一边"的可能。
 *
 * <p>绑定的是 {@code application.properties} 里的 {@code jwt.*}：
 * <ul>
 *   <li>{@code jwt.secret}（必填）—— 配置里<b>故意不写默认值</b>，
 *       缺环境变量时应用直接启动失败（fail-fast）；带一个仓库里公开的默认密钥跑起来
 *       等于任何人都能伪造令牌；</li>
 *   <li>{@code jwt.algorithm} —— 默认 {@link JwtAlgorithm#HS512}；</li>
 *   <li>{@code jwt.expiration} —— 有效期（毫秒），默认 12 小时。</li>
 * </ul>
 *
 * <p>用 record 而非普通类：配置是构造后不再变化的值，record 天然不可变，
 * 也省掉了 getter/setter 样板（Spring Boot 对 record 自动走构造器绑定）。
 *
 * @param secret     签名密钥原文，至少 {@link #algorithm() 算法} 要求的最短长度
 * @param algorithm  签名算法名，见 {@link JwtAlgorithm}
 * @param expiration 令牌有效期（毫秒）
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        String algorithm,
        @Positive long expiration
) {

    /**
     * 紧凑构造器：补默认值并做与长度相关的校验。
     *
     * <p>注意这里校验的是"密钥长度"而不是长度上限这类单字段约束，
     * 它依赖 {@code algorithm} 的取值，无法用普通 Bean Validation 注解表达，
     * 所以在构造器里显式做。这也是 fail-fast 的一部分：
     * 配置错了立刻启动失败，而不是等第一笔请求才报错。
     */
    public JwtProperties {
        if (algorithm == null || algorithm.isBlank()) {
            algorithm = JwtAlgorithm.HS512.name();
        }
        if (expiration <= 0) {
            expiration = 43_200_000L;
        }
        JwtAlgorithm.fromName(algorithm).validateKeyLength(secret);
    }

    /**
     * 解析后的签名算法。
     *
     * <p>方法名不叫 {@code algorithm()}，因为 record 已自动生成同名的访问器
     * （返回配置里的字符串），无法重载为不同返回类型。这里用 {@code resolved} 前缀
     * 表明"已解析成枚举"。
     *
     * @return 算法枚举
     */
    public JwtAlgorithm resolvedAlgorithm() {
        return JwtAlgorithm.fromName(algorithm);
    }

    /**
     * 由密钥构造签名用的 {@link SecretKey}。
     *
     * <p>签发端与校验端都用这个方法构造密钥，保证两边字节完全一致
     * （包括字符集——两端都显式用 UTF-8，不依赖平台默认编码）。
     *
     * @return HMAC 密钥
     */
    public SecretKey secretKey() {
        return new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), resolvedAlgorithm().jcaName());
    }
}
