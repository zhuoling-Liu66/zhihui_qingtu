package com.example.zhihuiqingtu.admin.support;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 管理后台访问守卫。
 *
 * <p>不能只依赖 JWT 的 {@code role} claim：角色被后台降级后，旧令牌在过期前仍带着
 * {@code ROLE_ADMIN}。因此管理端每个请求都回查数据库中的当前角色。
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final CurrentUser currentUser;
    private final UserInfoRepository userInfoRepository;

    /**
     * 校验当前登录用户是管理员，并返回其学号/工号。
     *
     * @return 当前管理员学号
     * @throws BusinessException 未登录或不是管理员
     */
    public String requireAdmin() {
        return requireAdminUser().getStudentNo();
    }

    /**
     * 校验当前登录用户是管理员，并返回数据库中的最新用户信息。
     *
     * @return 当前管理员
     */
    public UserInfo requireAdminUser() {
        UserInfo userInfo = requireCurrentUser();
        if (!UserRoleEnum.ADMIN.getCode().equals(userInfo.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可以执行该操作");
        }
        return userInfo;
    }

    /**
     * 校验当前登录用户是管理端账号（管理员或运营人员），并返回用户信息。
     *
     * @return 当前管理端账号
     */
    public UserInfo requireStaffOrAdmin() {
        UserInfo userInfo = requireCurrentUser();
        if (!UserRoleEnum.ADMIN.getCode().equals(userInfo.getUserRole())
                && !UserRoleEnum.STAFF.getCode().equals(userInfo.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理端账号可以执行该操作");
        }
        return userInfo;
    }

    /**
     * 校验当前登录用户是运营人员（非管理员）。
     *
     * <p>运营人员发布的学分活动仍需管理员审核；管理员若要发布活动，
     * 走 {@code POST /api/admin/activities}（全类型、免审核），不复用本入口。
     *
     * @return 当前运营人员
     */
    public UserInfo requireStaff() {
        UserInfo userInfo = requireCurrentUser();
        if (!UserRoleEnum.STAFF.getCode().equals(userInfo.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅运营人员账号可以发布学分活动");
        }
        return userInfo;
    }

    private UserInfo requireCurrentUser() {
        String userId = currentUser.requireUserId();
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理端账号不存在");
        }
        return userInfo;
    }
}
