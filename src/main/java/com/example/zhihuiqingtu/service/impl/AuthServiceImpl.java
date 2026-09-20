package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.example.zhihuiqingtu.common.JwtUtil;
import com.example.zhihuiqingtu.dto.auth.LoginRequest;
import com.example.zhihuiqingtu.dto.auth.RegisterRequest;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.service.AuthService;
import com.example.zhihuiqingtu.vo.auth.LoginResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现。
 * <p>
 * 除了注册/登录本身，本类还用 Redis 实现了<b>登录失败锁定</b>（防密码爆破）。
 * 这是「TTL 才是 Redis 核心武器」这句话最典型的用例：锁定这件事的本质是"过 15 分钟自动解除"，
 * 用 MySQL 做要么加一个 {@code locked_until} 列并在每次登录时比较时间，要么写定时任务扫表清零；
 * 而 Redis 的 key 到期自己就没了，<b>用"自动过期"代替了"删除操作"</b>。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * 允许的连续失败次数，超过即锁定。
     */
    private static final int MAX_LOGIN_FAIL = 5;

    /**
     * 失败计数的存活时间，也是锁定时长。
     * <p>
     * 用 TTL 而不是"手动清零"来实现"15 分钟后解锁"，见类注释。
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final UserInfoRepository userInfoRepository;
    private final JwtUtil jwtUtil;
    /**
     * 密码编码器，由 {@code config/PasswordConfig} 注册成 Bean 后注入。
     * 这里依赖的是接口而不是某个实现类，换算法时本类无需改动。
     */
    private final PasswordEncoder passwordEncoder;
    /**
     * 登录失败计数用字符串模板。
     * <p>
     * 这里不需要 JSON 序列化（只存一个数字），用 {@code StringRedisTemplate} 更直接，
     * 也能在 {@code redis-cli get} 里直接看到 {@code "5"} 而不是带引号的 JSON。
     */
    private final StringRedisTemplate stringRedisTemplate;
    /**
     * 令牌黑名单。登出时把当前令牌的 jti 记进去，拦截器每次校验时查一次。
     */
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse register(RegisterRequest request) {
        if (existsActiveUser(UserInfo::getStudentNo, request.getStudentNo())) {
            throw new BusinessException(4004, "学号或工号已存在");
        }
        if (request.getUserPhone() != null && !request.getUserPhone().isBlank()
                && existsActiveUser(UserInfo::getUserPhone, request.getUserPhone())) {
            throw new BusinessException(4005, "手机号已存在");
        }
        if (request.getUserEmail() != null && !request.getUserEmail().isBlank()
                && existsActiveUser(UserInfo::getUserEmail, request.getUserEmail())) {
            throw new BusinessException(4006, "邮箱已存在");
        }

        UserInfo userInfo = UserInfo.builder()
                .studentNo(request.getStudentNo())
                .userName(request.getUserName())
                .userPhone(request.getUserPhone())
                .userEmail(request.getUserEmail())
                .userRole(request.getUserRole())
                .publisherStatus(PublisherStatusEnum.ENABLED.getCode())
                .major(request.getMajor())
                .grade(request.getGrade())
                // encode() 内部会自动生成随机盐，并把"盐 + 哈希"一起编码成 60 个字符的字符串。
                // 也就是说数据库的 password_hash 一列既存了盐也存了哈希，不需要额外加字段。
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userInfoRepository.insert(userInfo);

        return UserProfileResponse.builder()
                .userId(userInfo.getStudentNo())
                .studentNo(userInfo.getStudentNo())
                .userName(userInfo.getUserName())
                .userPhone(userInfo.getUserPhone())
                .userEmail(userInfo.getUserEmail())
                .userRole(userInfo.getUserRole())
                .publisherStatus(userInfo.getPublisherStatus())
                .major(userInfo.getMajor())
                .grade(userInfo.getGrade())
                .interestTags(Collections.emptyList())
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 失败计数 key：业务域:对象:标识 三段式命名（auth:login:fail:{studentNo}）
        String failKey = "auth:login:fail:" + request.getStudentNo();

        // 第 1 步：先看是否已被锁定。这一步必须最早执行 ——
        // 锁定期间连"用户存不存在""密码对不对"都不该透露，否则可以靠响应差异继续试探。
        checkLoginLocked(failKey);

        UserInfo userInfo = userInfoRepository.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getStudentNo, request.getStudentNo())
                .last("LIMIT 1"));
        if (userInfo == null) {
            // 用户不存在也要计入失败次数（见类注释里的说明），否则攻击者可以用
            // "返回 4007 还是 4008" 来枚举系统中存在哪些学号。
            recordLoginFailure(failKey);
            throw new BusinessException(4007, "用户不存在");
        }
        // 注意这里必须用 matches(明文, 数据库里的密文)，不能再用 equals 比较两个哈希。
        // 因为 BCrypt 每次编码用的盐都是随机的，同一个密码两次 encode 出来的结果不同，
        // 只有 matches() 知道怎么从密文里把盐拆出来、重算后再比对。
        if (!passwordEncoder.matches(request.getPassword(), userInfo.getPasswordHash())) {
            recordLoginFailure(failKey);
            throw new BusinessException(4008, "用户名或密码错误");
        }

        // 登录成功 → 清空失败计数。
        // 不清的话，"之前失败过几次"会一直累积，用户正常登录一段时间后再错一次就被锁了。
        stringRedisTemplate.delete(failKey);

        //生成token
        String token = jwtUtil.generateToken(userInfo.getStudentNo(), userInfo.getUserRole());
        return LoginResponse.builder()
                .userId(userInfo.getStudentNo())
                .studentNo(userInfo.getStudentNo())
                .userName(userInfo.getUserName())
                .userRole(userInfo.getUserRole())
                .publisherStatus(userInfo.getPublisherStatus())
                .major(userInfo.getMajor())
                .grade(userInfo.getGrade())
                .token(token)
                .build();
    }

    /**
     * 检查该学号是否已被锁定，已锁定则直接抛 4029。
     * <p>
     * 放在最前面执行：锁定期间连"用户是否存在""密码对不对"都不该告诉调用方，
     * 否则可以通过响应差异继续试探。
     *
     * @param failKey 失败计数 key
     */
    private void checkLoginLocked(String failKey) {
        String failCount = stringRedisTemplate.opsForValue().get(failKey);
        if (failCount == null || Integer.parseInt(failCount) < MAX_LOGIN_FAIL) {
            return;
        }
        Long ttlSeconds = stringRedisTemplate.getExpire(failKey, TimeUnit.SECONDS);
        long remainMinutes = ttlSeconds == null || ttlSeconds <= 0 ? 0 : (ttlSeconds / 60 + 1);
        throw new BusinessException(4029, "密码错误次数过多，请 " + remainMinutes + " 分钟后再试");
    }

    /**
     * 记一次登录失败。
     * <p>
     * <b>这里有一个 Redis 计数器最经典的坑：{@code INCR} 和 {@code EXPIRE} 不是原子的。</b>
     * 直觉写法是「先 {@code increment(key)}，再 {@code expire(key, 15min)}」，
     * 但如果第一步成功、第二步之前服务重启或抛异常，这个 key 就<b>永不过期</b>了 ——
     * 用户会被<b>永久锁定</b>，必须人工上 Redis 删 key 才能恢复。
     * <p>
     * 正确写法是先用 {@code SET ... NX EX} 建一个带 TTL 的 key，再 {@code INCR}：
     * {@code NX} 保证只在"key 第一次被创建"时执行，所以 TTL 一定会在创建时设上，
     * 后续的 {@code INCR} 不会覆盖它，也就不会出现无 TTL 的 key。
     * <p>
     * 顺序绝对不能颠倒 —— 先 {@code INCR} 后 {@code setIfAbsent} 的话，
     * {@code setIfAbsent} 会因为 key 已存在而空转，TTL 永远设不上，等于没写。
     *
     * @param failKey 失败计数 key
     */
    private void recordLoginFailure(String failKey) {
        // 第一步：确保 key 存在且带 TTL（NX = 只在不存在时生效，不会覆盖已有的计数）
        stringRedisTemplate.opsForValue().setIfAbsent(failKey, "0", LOCK_TTL);
        // 第二步：此时 key 一定有 TTL，再自增就是安全的
        stringRedisTemplate.opsForValue().increment(failKey);
    }

    private boolean existsActiveUser(SFunction<UserInfo, ?> column, Object value) {
        return userInfoRepository.selectCount(new LambdaQueryWrapper<UserInfo>().eq(column, value)) > 0;
    }
}
