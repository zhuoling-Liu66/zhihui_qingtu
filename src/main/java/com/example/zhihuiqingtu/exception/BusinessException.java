package com.example.zhihuiqingtu.exception;

import lombok.Getter;

/**
 * 业务异常定义。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
