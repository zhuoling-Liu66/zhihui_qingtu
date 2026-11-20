package com.example.zhihuiqingtu.auth.service.impl;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.example.zhihuiqingtu.security.jwt.JwtUtil;
import com.example.zhihuiqingtu.auth.dto.LoginRequest;
import com.example.zhihuiqingtu.auth.dto.RegisterRequest;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.AccountStatusEnum;
import com.example.zhihuiqingtu.user.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.auth.service.AuthService;
import com.example.zhihuiqingtu.auth.vo.LoginResponse;
import com.example.zhihuiqingtu.user.vo.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import com.example.zhihuiqingtu.security.support.TokenBlacklistService;
import com.example.zhihuiqingtu.config.PasswordConfig;
import com.example.zhihuiqingtu.security.JwtBlacklistValidator;
import com.example.zhihuiqingtu.security.UserRevocationValidator;

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
     * 令牌黑名单。登出时把当前令牌的 jti 写进去，
     * 之后由框架的令牌校验器（{@code JwtBlacklistValidator}）在每个请求的解码阶段检查。
     */
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public void logout(Jwt jwt) {
        tokenBlacklistService.blacklist(jwt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse register(RegisterRequest request) {
        if (existsActiveUser(UserInfo::getStudentNo, request.getStudentNo())) {
            throw new BusinessException(ErrorCode.CONFLICT, "学号或工号已存在");
        }
        if (request.getUserPhone() != null && !request.getUserPhone().isBlank()
                && existsActiveUser(UserInfo::getUserPhone, request.getUserPhone())) {
            throw new BusinessException(ErrorCode.CONFLICT, "手机号已存在");
        }
        if (request.getUserEmail() != null && !request.getUserEmail().isBlank()
                && existsActiveUser(UserInfo::getUserEmail, request.getUserEmail())) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已存在");
        }

        UserInfo userInfo = UserInfo.builder()
                .studentNo(request.getStudentNo())
                .userName(request.getUserName())
                .userPhone(request.getUserPhone())
                .userEmail(request.getUserEmail())
                .userRole(UserRoleEnum.STUDENT.getCode())
                // 注册只给“学生”角色，发布资格默认未开通：
                // 学生需在个人中心提交申请，由管理员审批后才可发布活动。
                // 已有用户不会被回写，保留原来的 publisher_status，避免影响历史活动。
                .publisherStatus(PublisherStatusEnum.DISABLED.getCode())
                .accountStatus(AccountStatusEnum.NORMAL.getCode())
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
        return doLogin(request, false);
    }

    @Override
    public LoginResponse loginAdmin(LoginRequest request) {
        return doLogin(request, true);
    }

    private LoginResponse doLogin(LoginRequest request, boolean adminPortal) {
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 注意这里必须用 matches(明文, 数据库里的密文)，不能再用 equals 比较两个哈希。
        // 因为 BCrypt 每次编码用的盐都是随机的，同一个密码两次 encode 出来的结果不同，
        // 只有 matches() 知道怎么从密文里把盐拆出来、重算后再比对。
        if (!passwordEncoder.matches(request.getPassword(), userInfo.getPasswordHash())) {
            recordLoginFailure(failKey);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }

        // 密码已验证通过，先清空失败计数；后续即使因入口不匹配被拒，也不应继续累积锁定次数。
        stringRedisTemplate.delete(failKey);

        // ★ 账号封禁：禁用账号即使密码正确也不发令牌。
        // 已签发令牌的即时失效由 UserRevocationValidator 负责（禁用时写入按用户撤销标记），
        // 这里拦住的是"禁用之后再来登录"的路径。注销（软删除）的账号在上面 selectOne
        // 就查不到（@TableLogic 自动过滤），走"用户不存在"分支，无需在此重复判断。
        if (AccountStatusEnum.DISABLED.getCode().equals(userInfo.getAccountStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // ★ 入口隔离：运营人员只能登录管理端，学生/老师不能登录管理端。
        boolean adminRole = UserRoleEnum.ADMIN.getCode().equals(userInfo.getUserRole());
        boolean staffRole = UserRoleEnum.STAFF.getCode().equals(userInfo.getUserRole());
        if (adminPortal && !adminRole && !staffRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账号不是管理端账号");
        }
        if (!adminPortal && staffRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "运营人员账号请登录管理后台");
        }

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
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "密码错误次数过多，请 " + remainMinutes + " 分钟后再试");
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
