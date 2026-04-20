package com.example.zhihuiqingtu.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回对象。
 * <p>
 * 后端接口不直接把数据“裸返回”给前端，而是统一包装成：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": ...
 * }
 * </pre>
 * 这样前端在处理不同接口时可以保持相同的解析方式。
 *
 * @param <T> data 字段中真正承载的数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 业务状态码。
     * 约定 0 表示成功，其他编码表示不同类型的失败。
     */
    private Integer code;

    /**
     * 提示信息。
     * 成功时可以是 success 或业务成功提示，失败时通常是错误说明。
     */
    private String message;

    /**
     * 真正的业务数据。
     * 比如活动详情、分页列表、用户信息等。
     */
    private T data;

    /**
     * 生成一个默认成功响应。
     *
     * @param data 返回数据
     * @param <T>  数据类型
     * @return 标准成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(0)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 生成一个带自定义提示语的成功响应。
     *
     * @param message 成功提示语
     * @param data    返回数据
     * @param <T>     数据类型
     * @return 标准成功响应对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(0)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 生成一个失败响应。
     *
     * @param code    错误码
     * @param message 错误提示
     * @param <T>     泛型占位，便于与成功响应保持统一类型结构
     * @return 标准失败响应对象
     */
    public static <T> ApiResponse<T> failure(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
