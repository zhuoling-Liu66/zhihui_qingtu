package com.example.zhihuiqingtu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 * <p>
 * 本项目原来用 {@code PasswordUtil.sha256()} 做裸摘要，存在两个问题：
 * <ol>
 *   <li>没有盐：同一个密码永远得到同一个摘要，攻击者可以用彩虹表直接查表还原；</li>
 *   <li>算得太快：SHA-256 是为"快"设计的，显卡每秒能算几十亿次，弱密码很容易被暴力跑出来。</li>
 * </ol>
 * 换成 BCrypt 后，这两个问题都由算法本身解决：
 * <ul>
 *   <li><b>自带随机盐</b>：每次编码都会生成一个新的随机盐并写进结果字符串，所以同一个密码
 *       每次编码出来的结果都不一样，彩虹表失效；</li>
 *   <li><b>故意算得慢</b>：通过 cost 因子控制计算量，让暴力破解的成本高到不现实。</li>
 * </ul>
 * <p>
 * 这里注册成 Bean 而不是静态工具类，是为了让业务代码依赖 {@link PasswordEncoder} 这个接口，
 * 而不是依赖某个具体实现 —— 以后想换成 Argon2 或 PBKDF2，只要改本类的返回值，业务代码一行都不用动。
 */
@Configuration
public class PasswordConfig {

    /**
     * 注册密码编码器。
     * <p>
     * {@code new BCryptPasswordEncoder()} 使用默认 cost 因子 10，即做 2^10 = 1024 轮哈希。
     * 这个默认值是 Spring Security 权衡"安全性"与"单次登录耗时（约几十毫秒）"后选定的，
     * 直接用它就够了。若确实需要调整（如对安全等级要求更高），可改成
     * {@code new BCryptPasswordEncoder(12)}，注意每 +1 计算耗时翻倍。
     *
     * @return BCrypt 实现的密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
