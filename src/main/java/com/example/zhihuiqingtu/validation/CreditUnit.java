package com.example.zhihuiqingtu.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制学分字段必须符合指定基础单位。
 */
@Documented
@Constraint(validatedBy = CreditUnitValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CreditUnit {

    String message() default "学分必须以0.05为单位";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String unit() default "0.05";
}
