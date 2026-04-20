package com.example.zhihuiqingtu.exception;

import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 当程序执行过程中发现“业务规则不允许继续”时，就抛出这个异常。
 * 例如：
 * 用户不存在、活动已结束、人数已满、学分类型非法等。
 * <p>
 * 这类异常和 NullPointerException、数据库连接失败这类“系统异常”不同，
 * 它们通常是“可以预期的业务失败”。
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码。
     */
    private final Integer code;

    /**
     * 创建业务异常对象。
     *
     * @param code    错误码
     * @param message 错误提示信息
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
