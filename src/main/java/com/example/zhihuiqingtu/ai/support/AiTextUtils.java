package com.example.zhihuiqingtu.ai.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * AI 模块的文本/时间工具。
 *
 * <p>时间解析必须"宽容"：模型可能返回 {@code 2026-10-24}、{@code 2026-10-24 14:00}、
 * {@code 2026-10-24T14:00:00} 三种写法中的任意一种。严格只认一种的话，
 * 表现是"用户明明说了时间，助手却查不到活动"。
 */
public final class AiTextUtils {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    private AiTextUtils() {
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 学号脱敏：{@code 20230001 -> 2023****01}。
     * <p>日志里出现完整学号也算个人信息泄露，脱敏后再打。
     */
    public static String mask(String userId) {
        if (userId == null || userId.length() <= 6) {
            return "***";
        }
        return userId.substring(0, 4) + "****" + userId.substring(userId.length() - 2);
    }

    /**
     * 宽容解析时间；无法解析返回 null（调用方决定是忽略该条件还是报 400）。
     */
    public static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式
            }
        }
        return null;
    }

    /** 仅日期时补齐到当天 23:59:59，用于"截止时间"上界。 */
    public static LocalDateTime endOfDayIfDateOnly(String raw) {
        LocalDateTime parsed = parseDateTime(raw);
        if (parsed == null) {
            return null;
        }
        boolean dateOnly = raw != null && raw.trim().length() <= 10;
        return dateOnly ? parsed.toLocalDate().atTime(LocalTime.MAX) : parsed;
    }
}
