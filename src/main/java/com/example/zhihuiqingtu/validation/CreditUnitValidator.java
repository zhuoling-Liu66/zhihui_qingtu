package com.example.zhihuiqingtu.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * {@link CreditUnit} 注解对应的实际校验器。
 * <p>
 * 它负责校验一个 BigDecimal 数值是否满足指定的最小步进单位。
 */
public class CreditUnitValidator implements ConstraintValidator<CreditUnit, BigDecimal> {

    /**
     * 注解中配置的步进单位。
     */
    private BigDecimal unit;

    /**
     * 允许的小数位上限。
     * 例如单位 0.05 的 scale 是 2。
     */
    private int allowedScale;

    /**
     * 初始化校验器，读取注解上的参数。
     *
     * @param constraintAnnotation 当前字段上的 CreditUnit 注解
     */
    @Override
    public void initialize(CreditUnit constraintAnnotation) {
        this.unit = new BigDecimal(constraintAnnotation.unit());
        this.allowedScale = Math.max(0, this.unit.stripTrailingZeros().scale());
    }

    /**
     * 执行校验逻辑。
     *
     * @param value   待校验的字段值
     * @param context 校验上下文
     * @return true 表示通过，false 表示不通过
     */
    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            // 这里只校验“单位是否正确”，是否允许为空交给 @NotNull 等注解处理。
            return true;
        }

        BigDecimal normalizedValue = value.stripTrailingZeros();
        if (normalizedValue.scale() > allowedScale) {
            return false;
        }

        return normalizedValue.remainder(unit).compareTo(BigDecimal.ZERO) == 0;
    }
}
