package com.example.zhihuiqingtu.exception;

import com.example.zhihuiqingtu.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 * <p>
 * 用于统一拦截控制器抛出的异常，并包装成统一的 JSON 返回格式。
 * 这样前端不需要针对不同异常分别解析不同响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理项目中的业务异常。
     *
     * @param exception 业务异常
     * @return 统一失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.failure(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理请求体 DTO 校验失败。
     * 比如 @NotBlank、@NotNull、@Size 等约束不满足时会进入这里。
     *
     * @param exception 参数校验异常
     * @return 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ApiResponse.failure(4000, message);
    }

    /**
     * 处理参数绑定异常。
     *
     * @param exception 参数绑定异常
     * @return 统一失败响应
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + error.getDefaultMessage())
                .orElse("参数绑定失败");
        return ApiResponse.failure(4001, message);
    }

    /**
     * 处理路径参数、请求参数等位置的约束校验异常。
     *
     * @param exception 约束校验异常
     * @return 统一失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        return ApiResponse.failure(4002, exception.getMessage());
    }

    /**
     * 处理请求体 JSON 格式错误。
     *
     * @param exception 消息不可读异常
     * @return 统一失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        return ApiResponse.failure(4003, "请求体格式错误");
    }

    /**
     * 处理静态资源不存在的情况。
     *
     * @param exception 资源不存在异常
     * @return 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(4040, "静态资源不存在"));
    }

    /**
     * 兜底处理其他未显式捕获的异常。
     *
     * @param exception 未知异常
     * @return 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.failure(5000, exception.getMessage() == null ? "系统内部异常" : exception.getMessage());
    }
}
