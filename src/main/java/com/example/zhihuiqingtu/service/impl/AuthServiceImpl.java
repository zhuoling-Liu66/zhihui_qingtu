package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.example.zhihuiqingtu.common.PasswordUtil;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * 认证服务实现。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserInfoRepository userInfoRepository;

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
                .passwordHash(PasswordUtil.sha256(request.getPassword()))
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
        UserInfo userInfo = userInfoRepository.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getStudentNo, request.getStudentNo())
                .last("LIMIT 1"));
        if (userInfo == null) {
            throw new BusinessException(4007, "用户不存在");
        }
        if (!PasswordUtil.sha256(request.getPassword()).equals(userInfo.getPasswordHash())) {
            throw new BusinessException(4008, "用户名或密码错误");
        }
        return LoginResponse.builder()
                .userId(userInfo.getStudentNo())
                .studentNo(userInfo.getStudentNo())
                .userName(userInfo.getUserName())
                .userRole(userInfo.getUserRole())
                .publisherStatus(userInfo.getPublisherStatus())
                .major(userInfo.getMajor())
                .grade(userInfo.getGrade())
                .build();
    }

    private boolean existsActiveUser(SFunction<UserInfo, ?> column, Object value) {
        return userInfoRepository.selectCount(new LambdaQueryWrapper<UserInfo>().eq(column, value)) > 0;
    }
}
