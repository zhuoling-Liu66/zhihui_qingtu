package com.example.zhihuiqingtu.admin.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.user.dto.AdminCreateStaffRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminResetPasswordRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdateAccountStatusRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdatePublisherStatusRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdateRoleRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUserQueryRequest;
import com.example.zhihuiqingtu.admin.support.AdminPages;
import com.example.zhihuiqingtu.admin.user.vo.AdminUserVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.publisher.entity.PublisherPermissionRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.AccountStatusEnum;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordStatusEnum;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordTypeEnum;
import com.example.zhihuiqingtu.user.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.publisher.repository.PublisherPermissionRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.support.UserRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户服务。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminGuard adminGuard;
    private final UserInfoRepository userInfoRepository;
    private final PublisherPermissionRecordRepository publisherRecordRepository;
    private final PasswordEncoder passwordEncoder;
    /**
     * 按用户撤销令牌：禁用/注销账号时调用，使已签发令牌立即失效。
     */
    private final UserRevocationService userRevocationService;

    /**
     * 分页查询用户，支持按学号/姓名/手机号/邮箱关键词和角色、发布权限筛选。
     */
    public PageResponse<AdminUserVO> queryUsers(AdminUserQueryRequest request) {
        adminGuard.requireAdmin();
        String keyword = StringUtils.hasText(request.getKeyword()) ? request.getKeyword().trim() : null;

        LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<UserInfo>()
                .eq(request.getUserRole() != null, UserInfo::getUserRole, request.getUserRole())
                .eq(request.getPublisherStatus() != null, UserInfo::getPublisherStatus, request.getPublisherStatus());
        if (keyword != null) {
            queryWrapper.and(wrapper -> wrapper
                    .like(UserInfo::getStudentNo, keyword)
                    .or().like(UserInfo::getUserName, keyword)
                    .or().like(UserInfo::getUserPhone, keyword)
                    .or().like(UserInfo::getUserEmail, keyword));
        }
        queryWrapper.orderByDesc(UserInfo::getCreateTime);

        Page<UserInfo> page = userInfoRepository.selectPage(
                AdminPages.of(request.getPageNo(), request.getPageSize()),
                queryWrapper
        );
        Page<AdminUserVO> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResponse.from(responsePage);
    }

    /**
     * 调整用户角色。
     *
     * <p>角色编码写进 JWT，修改后需要用户重新登录才会在新令牌中生效；
     * 管理端接口本身通过 {@link AdminGuard} 回查数据库，因此降级会立即失去管理权限。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO updateRole(String studentNo, AdminUpdateRoleRequest request) {
        String operatorId = adminGuard.requireAdmin();
        UserRoleEnum targetRole = UserRoleEnum.fromCode(request.getUserRole());
        if (targetRole == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色仅支持学生、管理员、老师或运营人员");
        }
        if (operatorId.equals(studentNo)) {
            throw new BusinessException(ErrorCode.CONFLICT, "不能修改自己的角色，避免误操作导致无法登录后台");
        }

        UserInfo userInfo = requireUser(studentNo);
        if (targetRole.getCode().equals(userInfo.getUserRole())) {
            return toVO(userInfo);
        }
        if (UserRoleEnum.ADMIN.getCode().equals(userInfo.getUserRole())) {
            long adminCount = userInfoRepository.selectCount(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUserRole, UserRoleEnum.ADMIN.getCode()));
            if (adminCount <= 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "系统至少需要保留一名管理员");
            }
        }

        userInfoRepository.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getStudentNo, studentNo)
                .set(UserInfo::getUserRole, targetRole.getCode()));
        userInfo.setUserRole(targetRole.getCode());
        return toVO(userInfo);
    }

    /**
     * 创建管理端运营人员账号（非管理员）。
     *
     * <p>该账号只能登录管理端发布/编辑学分活动并提交审核；客户端登录会被拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO createStaff(AdminCreateStaffRequest request) {
        adminGuard.requireAdmin();
        if (userInfoRepository.selectById(request.getStudentNo()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工号已存在");
        }
        if (StringUtils.hasText(request.getUserPhone())
                && existsByColumn(UserInfo::getUserPhone, request.getUserPhone())) {
            throw new BusinessException(ErrorCode.CONFLICT, "手机号已存在");
        }
        if (StringUtils.hasText(request.getUserEmail())
                && existsByColumn(UserInfo::getUserEmail, request.getUserEmail())) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已存在");
        }

        UserInfo staff = UserInfo.builder()
                .studentNo(request.getStudentNo())
                .userName(request.getUserName())
                .userPhone(request.getUserPhone())
                .userEmail(request.getUserEmail())
                .userRole(UserRoleEnum.STAFF.getCode())
                .publisherStatus(PublisherStatusEnum.DISABLED.getCode())
                .accountStatus(AccountStatusEnum.NORMAL.getCode())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userInfoRepository.insert(staff);
        return toVO(staff);
    }

    /**
     * 重置管理端账号密码。
     *
     * <p>只允许重置管理员或运营人员账号，避免管理端被用来改普通学生的密码。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String studentNo, AdminResetPasswordRequest request) {
        adminGuard.requireAdmin();
        UserInfo target = requireUser(studentNo);
        if (!UserRoleEnum.ADMIN.getCode().equals(target.getUserRole())
                && !UserRoleEnum.STAFF.getCode().equals(target.getUserRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅可重置管理端账号的密码");
        }
        userInfoRepository.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getStudentNo, studentNo)
                .set(UserInfo::getPasswordHash, passwordEncoder.encode(request.getPassword())));
    }

    /**
     * 开通或收回用户的活动发布权限。
     *
     * <p>与“审批学生申请”不同，这里是管理员直接调整，但仍会写入一条
     * {@link PublisherPermissionRecord}（类型为管理员开通/收回），并在同一事务内
     * 关闭该用户仍在待审批的学生申请，保证管理后台的每一次权限变更都有迹可循。
     *
     * <p>权限变更不会回写或下架用户已经发布的活动：历史活动是否继续展示由活动本身
     * 的状态决定，管理员如需处理可到活动管理中下架或删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO updatePublisherStatus(String studentNo, AdminUpdatePublisherStatusRequest request) {
        String operatorId = adminGuard.requireAdmin();
        UserInfo userInfo = requireUser(studentNo);
        Integer targetStatus = request.getPublisherStatus();
        if (targetStatus.equals(userInfo.getPublisherStatus())) {
            // 状态没变化就不产生无意义的审计记录。
            return toVO(userInfo);
        }

        int updated = userInfoRepository.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getStudentNo, studentNo)
                .set(UserInfo::getPublisherStatus, targetStatus));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户状态已变化，请刷新后重试");
        }

        LocalDateTime operateTime = LocalDateTime.now();
        boolean enabled = PublisherStatusEnum.ENABLED.getCode().equals(targetStatus);
        publisherRecordRepository.insert(PublisherPermissionRecord.builder()
                .studentNo(studentNo)
                .recordType(enabled
                        ? PublisherRecordTypeEnum.GRANT.getCode()
                        : PublisherRecordTypeEnum.REVOKE.getCode())
                .recordStatus(PublisherRecordStatusEnum.APPROVED.getCode())
                .applyReason(enabled ? "管理员在用户管理中直接开通" : "管理员在用户管理中直接收回")
                .operatorUserId(operatorId)
                .operateTime(operateTime)
                .build());

        // 直接调整权限后，原待审批申请不再有意义，统一关闭而不是继续留在审批队列里。
        publisherRecordRepository.update(null,
                new LambdaUpdateWrapper<PublisherPermissionRecord>()
                        .eq(PublisherPermissionRecord::getStudentNo, studentNo)
                        .eq(PublisherPermissionRecord::getRecordType, PublisherRecordTypeEnum.APPLY.getCode())
                        .eq(PublisherPermissionRecord::getRecordStatus, PublisherRecordStatusEnum.PENDING.getCode())
                        .set(PublisherPermissionRecord::getRecordStatus, PublisherRecordStatusEnum.CLOSED.getCode())
                        .set(PublisherPermissionRecord::getOperatorUserId, operatorId)
                        .set(PublisherPermissionRecord::getOperateTime, operateTime)
                        .set(PublisherPermissionRecord::getRejectReason,
                                enabled ? "管理员已直接开通发布权限" : "管理员已直接收回发布权限"));

        userInfo.setPublisherStatus(targetStatus);
        return toVO(userInfo);
    }

    /**
     * 禁用/启用管理端普通账号（运营人员）。
     *
     * <p><b>只允许操作 STAFF</b>：管理员账号不能被禁用（否则可能把系统锁死），
     * 学生/老师属于客户端账号，不在本功能范围内。
     *
     * <p><b>禁用立即生效</b>：除了改库，还写入按用户撤销标记
     * （{@link UserRevocationService}），使该用户已签发的全部令牌在下次请求时即被拒。
     * 只改库不撤销令牌的话，旧令牌在过期前（默认 12 小时）依然可用，等于没禁。
     *
     * <p><b>启用时不删除撤销标记</b>：删除会让禁用前的旧令牌复活。
     * 保留标记后旧令牌继续被拒，用户重新登录即可拿到新令牌。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO updateAccountStatus(String studentNo, AdminUpdateAccountStatusRequest request) {
        adminGuard.requireAdmin();
        Integer targetStatus = request.getAccountStatus();
        if (!AccountStatusEnum.NORMAL.getCode().equals(targetStatus)
                && !AccountStatusEnum.DISABLED.getCode().equals(targetStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账号状态仅支持正常或禁用");
        }
        UserInfo userInfo = requireStaffUser(studentNo);
        if (targetStatus.equals(userInfo.getAccountStatus())) {
            // 状态没变化就不写库、也不刷新撤销标记。
            return toVO(userInfo);
        }

        int updated = userInfoRepository.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getStudentNo, studentNo)
                .set(UserInfo::getAccountStatus, targetStatus));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户状态已变化，请刷新后重试");
        }
        if (AccountStatusEnum.DISABLED.getCode().equals(targetStatus)) {
            userRevocationService.revoke(studentNo);
        }
        userInfo.setAccountStatus(targetStatus);
        return toVO(userInfo);
    }

    /**
     * 注销管理端普通账号（运营人员）。
     *
     * <p>软删除：{@code deleteById} 经 {@code @TableLogic} 翻译为
     * {@code UPDATE ... SET delete_flag=1}，历史数据保留，所有查询自动将其过滤。
     * 注销后登录走"用户不存在"分支；同时写入按用户撤销标记，已签发令牌立即失效。
     *
     * <p>与禁用同样只允许操作 STAFF，防止误删管理员或客户端学生账号。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteStaff(String studentNo) {
        adminGuard.requireAdmin();
        requireStaffUser(studentNo);
        userInfoRepository.deleteById(studentNo);
        userRevocationService.revoke(studentNo);
    }

    /**
     * 最近注册的用户（概览页用）。
     */
    public List<AdminUserVO> listRecent(int limit) {
        Page<UserInfo> page = userInfoRepository.selectPage(
                new Page<>(1, Math.max(limit, 1)),
                new LambdaQueryWrapper<UserInfo>().orderByDesc(UserInfo::getCreateTime)
        );
        return page.getRecords().stream().map(this::toVO).toList();
    }

    private UserInfo requireUser(String studentNo) {
        UserInfo userInfo = userInfoRepository.selectById(studentNo);
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return userInfo;
    }

    /**
     * 查用户并限定为运营人员（管理端普通账号）。
     *
     * <p>禁用/注销是管理端账号治理操作，目标只能是 STAFF：
     * 管理员不能被禁（可能把系统锁死），学生/老师归客户端，不在本入口处理。
     */
    private UserInfo requireStaffUser(String studentNo) {
        UserInfo userInfo = requireUser(studentNo);
        if (!UserRoleEnum.STAFF.getCode().equals(userInfo.getUserRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅可禁用或注销管理端的运营人员账号");
        }
        return userInfo;
    }

    private boolean existsByColumn(SFunction<UserInfo, ?> column, Object value) {
        return userInfoRepository.selectCount(new LambdaQueryWrapper<UserInfo>().eq(column, value)) > 0;
    }

    private AdminUserVO toVO(UserInfo userInfo) {
        return AdminUserVO.builder()
                .studentNo(userInfo.getStudentNo())
                .userName(userInfo.getUserName())
                .userPhone(userInfo.getUserPhone())
                .userEmail(userInfo.getUserEmail())
                .userRole(userInfo.getUserRole())
                .publisherStatus(userInfo.getPublisherStatus())
                .accountStatus(userInfo.getAccountStatus())
                .major(userInfo.getMajor())
                .grade(userInfo.getGrade())
                .createTime(userInfo.getCreateTime())
                .build();
    }
}
