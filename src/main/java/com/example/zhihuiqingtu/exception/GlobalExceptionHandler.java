package com.example.zhihuiqingtu.exception;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
 *
 * <p>统一把异常包装成 {@link ApiResponse} 结构，并让 <b>HTTP 状态码与业务码保持一致</b>：
 * 业务码取自 {@link ErrorCode}（与 HTTP 语义对齐），响应状态码取同一个值。
 * 这样访问日志、网关、监控告警与前端都能按标准 HTTP 语义处理，
 * 不必约定"HTTP 200 里藏着失败"这类规则。
 *
 * <p><b>维护约束</b>：新增 {@code @ExceptionHandler} 时必须同时确定 HTTP 状态码，
 * 只返回 {@code ApiResponse} 会让该异常落回 HTTP 200，破坏上述一致性。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理项目中的业务异常。
     *
     * @param exception 业务异常
     * @return 与业务码同值的 HTTP 状态码 + 统一失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        Integer code = exception.getCode();
        ApiResponse<Void> body = ApiResponse.failure(code, exception.getMessage());
        // code 可能被调用方传入非 HTTP 状态码的取值，此时退回 400 而不是抛 IllegalArgument
        HttpStatus status = HttpStatus.resolve(code == null ? 400 : code);
        return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status).body(body);
    }

    /**
     * 处理请求体 DTO 校验失败（{@code @NotBlank}、{@code @NotNull}、{@code @Size} 等）。
     *
     * @param exception 参数校验异常
     * @return 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.PARAM_ERROR, message));
    }

    /**
     * 处理查询参数绑定异常（{@code @ModelAttribute} 绑定失败）。
     *
     * @param exception 参数绑定异常
     * @return 统一失败响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + error.getDefaultMessage())
                .orElse("参数绑定失败");
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.PARAM_ERROR, message));
    }

    /**
     * 处理路径参数、请求参数的约束校验异常（需 {@code @Validated} 生效）。
     *
     * @param exception 约束校验异常
     * @return 统一失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.PARAM_ERROR, exception.getMessage()));
    }

    /**
     * 处理请求体 JSON 格式错误。
     *
     * @param exception 消息不可读异常
     * @return 统一失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.PARAM_ERROR, "请求体格式错误"));
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
                .body(ApiResponse.failure(ErrorCode.NOT_FOUND, "资源不存在"));
    }

    /**
     * 兜底处理其他未显式捕获的异常。
     *
     * <p><b>不回显 {@code exception.getMessage()}</b>：原始异常信息包含 SQL 语句片段、
     * 表名、列名、类名与文件路径，直接返回给客户端等同于向外部泄漏内部实现。
     * 原始信息记录到服务端日志，客户端只得到通用提示。
     *
     * @param exception 未知异常
     * @return 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("未处理异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorCode.SYSTEM_ERROR, "系统内部异常，请稍后重试"));
    }
}
