package com.example.zhihuiqingtu.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * 学分基础单位校验器。
 */
public class CreditUnitValidator implements ConstraintValidator<CreditUnit, BigDecimal> {

    private BigDecimal unit;
    private int allowedScale;

    @Override
    public void initialize(CreditUnit constraintAnnotation) {
        this.unit = new BigDecimal(constraintAnnotation.unit());
        this.allowedScale = Math.max(0, this.unit.stripTrailingZeros().scale());
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        BigDecimal normalizedValue = value.stripTrailingZeros();
        if (normalizedValue.scale() > allowedScale) {
            return false;
        }

        return normalizedValue.remainder(unit).compareTo(BigDecimal.ZERO) == 0;
    }
}
