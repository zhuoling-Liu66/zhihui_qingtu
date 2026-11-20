package com.example.zhihuiqingtu.user.vo;

import com.example.zhihuiqingtu.activity.vo.ActivityTagVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户资料返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    /**
     * 用户唯一标识，值为学号或工号。
     */
    private String userId;

    /**
     * 学号或工号。
     */
    private String studentNo;

    /**
     * 用户姓名。
     */
    private String userName;

    /**
     * 手机号。
     */
    private String userPhone;

    /**
     * 邮箱。
     */
    private String userEmail;

    /**
     * 用户角色。
     */
    private Integer userRole;

    /**
     * 发布权限状态。
     */
    private Integer publisherStatus;

    /**
     * 专业。
     */
    private String major;

    /**
     * 年级。
     */
    private String grade;

    /**
     * 兴趣标签列表。
     */
    private List<ActivityTagVO> interestTags;
}
