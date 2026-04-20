package com.example.zhihuiqingtu.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义校验注解：限制学分必须符合指定的最小计量单位。
 * <p>
 * 例如 unit = 0.05 时，合法值应为：
 * 0、0.05、0.10、0.15……
 */
@Documented
@Constraint(validatedBy = CreditUnitValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CreditUnit {

    /**
     * 校验失败时的默认提示语。
     */
    String message() default "学分必须以 0.05 为单位";

    /**
     * 分组校验参数，当前项目没有额外扩展使用。
     */
    Class<?>[] groups() default {};

    /**
     * 载荷参数，属于 Bean Validation 扩展位，当前项目没有额外使用。
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 步进单位，默认值为 0.05。
     */
    String unit() default "0.05";
}
