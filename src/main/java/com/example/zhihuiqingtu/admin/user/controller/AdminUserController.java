package com.example.zhihuiqingtu.admin.user.controller;

import com.example.zhihuiqingtu.admin.user.dto.AdminCreateStaffRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminResetPasswordRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdateAccountStatusRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdatePublisherStatusRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdateRoleRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUserQueryRequest;
import com.example.zhihuiqingtu.admin.user.service.AdminUserService;
import com.example.zhihuiqingtu.admin.user.vo.AdminUserVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台用户管理接口。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 分页查询用户。
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminUserVO>> queryUsers(@ModelAttribute AdminUserQueryRequest request) {
        return ApiResponse.success(adminUserService.queryUsers(request));
    }

    /**
     * 调整用户角色（学生 / 管理员 / 老师）。
     */
    @PatchMapping("/{studentNo}/role")
    public ApiResponse<AdminUserVO> updateRole(@PathVariable String studentNo,
                                               @Valid @RequestBody AdminUpdateRoleRequest request) {
        return ApiResponse.success("角色已更新，该用户重新登录后生效",
                adminUserService.updateRole(studentNo, request));
    }

    /**
     * 创建管理端运营人员账号（非管理员，只能发布学分活动）。
     */
    @PostMapping("/staff")
    public ApiResponse<AdminUserVO> createStaff(@Valid @RequestBody AdminCreateStaffRequest request) {
        return ApiResponse.success("运营人员账号已创建",
                adminUserService.createStaff(request));
    }

    /**
     * 重置管理端账号密码。
     */
    @PatchMapping("/{studentNo}/password")
    public ApiResponse<Void> resetPassword(@PathVariable String studentNo,
                                           @Valid @RequestBody AdminResetPasswordRequest request) {
        adminUserService.resetPassword(studentNo, request);
        return ApiResponse.success("密码已重置", null);
    }

    /**
     * 开通或收回用户的活动发布权限。
     */
    @PatchMapping("/{studentNo}/publisher-status")
    public ApiResponse<AdminUserVO> updatePublisherStatus(
            @PathVariable String studentNo,
            @Valid @RequestBody AdminUpdatePublisherStatusRequest request) {
        return ApiResponse.success("发布权限已更新",
                adminUserService.updatePublisherStatus(studentNo, request));
    }

    /**
     * 禁用/启用管理端普通账号（运营人员）。
     *
     * <p>禁用立即生效：登录被拒，已签发令牌同时作废（按用户撤销标记）。
     */
    @PatchMapping("/{studentNo}/status")
    public ApiResponse<AdminUserVO> updateAccountStatus(
            @PathVariable String studentNo,
            @Valid @RequestBody AdminUpdateAccountStatusRequest request) {
        return ApiResponse.success("账号状态已更新，立即生效",
                adminUserService.updateAccountStatus(studentNo, request));
    }

    /**
     * 注销管理端普通账号（运营人员），软删除。
     *
     * <p>注销后账号无法登录、令牌立即作废；历史数据保留（delete_flag=1）。
     */
    @DeleteMapping("/{studentNo}")
    public ApiResponse<Void> deleteStaff(@PathVariable String studentNo) {
        adminUserService.deleteStaff(studentNo);
        return ApiResponse.success("账号已注销", null);
    }
}
