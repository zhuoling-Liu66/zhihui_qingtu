package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.validation.CreditUnit;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 学分基础单位校验测试。
 */
class CreditUnitValidatorTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void tearDown() {
        validator = null;
    }

    @Test
    void shouldAcceptValueAlignedToFiveCents() {
        CreditPayload payload = new CreditPayload(new BigDecimal("1.15"));

        int violations = validator.validate(payload).size();

        assertEquals(0, violations);
    }

    @Test
    void shouldRejectValueNotAlignedToFiveCents() {
        CreditPayload payload = new CreditPayload(new BigDecimal("1.12"));

        int violations = validator.validate(payload).size();

        assertEquals(1, violations);
    }

    @Test
    void shouldRejectValueWithMoreThanTwoDecimalPlaces() {
        CreditPayload payload = new CreditPayload(new BigDecimal("0.051"));

        int violations = validator.validate(payload).size();

        assertEquals(1, violations);
    }

    private record CreditPayload(@CreditUnit BigDecimal credit) {
    }
}
