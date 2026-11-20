package com.example.zhihuiqingtu.ai.service;

import com.example.zhihuiqingtu.ai.client.SpringAiChatGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 表格列识别：优先让模型判断表头与列，失败时用关键词规则兜底。
 *
 * <p>模型只输出“哪一列是什么”，不参与金额计算，也不写库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTableMappingService {

    private static final int SAMPLE_ROWS = 12;

    private static final List<String> STUDENT_KEYS = List.of("学号", "工号", "学生编号", "studentno", "studentid");
    private static final List<String> NAME_KEYS = List.of("姓名", "名字", "name");
    private static final List<String> HOURS_KEYS = List.of("时长", "小时", "志愿时长", "服务时长", "hours", "hour", "h");
    private static final List<String> CREDIT_KEYS = List.of("学分", "credit");
    private static final List<String> SEQ_KEYS = List.of("序号", "编号");

    private final SpringAiChatGateway gateway;
    private final ObjectMapper objectMapper;

    /**
     * 分析表格结构。
     *
     * @param rows        原始表格
     * @param instruction 用户补充说明（可为空）
     * @param valueKind   credit / hours，用于提示模型重点寻找哪一列
     */
    public TableMapping analyze(List<List<String>> rows, String instruction, String valueKind) {
        TableMapping rule = heuristic(rows);
        TableMapping ai = ai(rows, instruction, valueKind);
        if (ai == null) {
            return rule;
        }
        return new TableMapping(
                ai.headerRow() >= 0 ? ai.headerRow() : rule.headerRow(),
                firstNonNull(ai.studentNoCol(), rule.studentNoCol()),
                firstNonNull(ai.nameCol(), rule.nameCol()),
                firstNonNull(ai.hoursCol(), rule.hoursCol()),
                firstNonNull(ai.creditCol(), rule.creditCol()),
                ai.hoursCol() != null ? ai.hoursUnit() : rule.hoursUnit(),
                ai.notes(),
                true
        );
    }

    // ══════════════════ AI 识别 ══════════════════

    private TableMapping ai(List<List<String>> rows, String instruction, String valueKind) {
        if (!gateway.isAvailable()) {
            return null;
        }
        StringBuilder table = new StringBuilder();
        int limit = Math.min(rows.size(), SAMPLE_ROWS);
        for (int i = 0; i < limit; i++) {
            table.append(i).append(" | ").append(String.join(" | ", rows.get(i))).append('\n');
        }
        String system = """
                你是表格结构识别器。用户上传的表格里可能有合并的大标题行、空行、无关列。
                请找出表头所在行，以及学号、姓名、时长、学分四类列（不存在就给 null）。
                只输出一个 JSON 对象，不要 markdown 代码块、不要解释：
                {"headerRow":0,"studentNo":8,"name":1,"hours":9,"credit":null,
                 "hoursUnit":"hour|min","notes":"一句话说明"}
                下标都是 0 基，对应下面给出的“原始行号 | 单元格”表。
                “序号/编号”不是学号；时长列的单位若表头写“分钟/min”则 hoursUnit=min，否则 hour。
                """;
        String user = "本次活动要导入:" + ("credit".equals(valueKind) ? "学分（优先找学分列）" : "志愿时长（优先找时长/小时列）")
                + "\n用户说明:" + (instruction == null || instruction.isBlank() ? "无" : instruction)
                + "\n表格前" + limit + "行（原始行号从0开始）:\n" + table;
        String reply = gateway.completeText(system, user);
        if (reply == null || reply.isBlank()) {
            return null;
        }
        try {
            String json = extractJson(reply);
            JsonNode node = objectMapper.readTree(json);
            int headerRow = node.path("headerRow").asInt(0);
            if (headerRow < 0 || headerRow >= limit) {
                return null;
            }
            Integer studentNo = column(node, "studentNo", rows);
            Integer name = column(node, "name", rows);
            Integer hours = column(node, "hours", rows);
            Integer credit = column(node, "credit", rows);
            String unit = node.path("hoursUnit").asText("hour");
            unit = unit.toLowerCase(Locale.ROOT).contains("min") || unit.contains("分钟") ? "min" : "hour";
            String notes = node.path("notes").asText("");
            if (studentNo == null && name == null && hours == null && credit == null) {
                return null;
            }
            return new TableMapping(headerRow, studentNo, name, hours, credit, unit,
                    notes.isBlank() ? "AI 已识别表头与列" : notes, true);
        } catch (Exception exception) {
            log.warn("表格识别结果解析失败，降级为规则识别：{}", exception.getMessage());
            return null;
        }
    }

    private Integer column(JsonNode node, String field, List<List<String>> rows) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        int index = value.asInt(-1);
        if (index < 0) {
            return null;
        }
        for (List<String> row : rows) {
            if (index < row.size()) {
                return index;
            }
        }
        return null;
    }

    private String extractJson(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return reply.substring(start, end + 1);
        }
        return reply.trim();
    }

    // ══════════════════ 规则识别 ══════════════════

    private TableMapping heuristic(List<List<String>> rows) {
        int bestRow = -1;
        int bestScore = 0;
        int bestStudent = -1;
        int bestName = -1;
        int bestHours = -1;
        int bestCredit = -1;
        int limit = Math.min(rows.size(), SAMPLE_ROWS);
        for (int r = 0; r < limit; r++) {
            List<String> row = rows.get(r);
            int student = findColumn(row, STUDENT_KEYS, true);
            int name = findColumn(row, NAME_KEYS, false);
            int hours = findColumn(row, HOURS_KEYS, false);
            int credit = findColumn(row, CREDIT_KEYS, false);
            int distinct = (student >= 0 ? 1 : 0) + (name >= 0 ? 1 : 0) + (hours >= 0 ? 1 : 0) + (credit >= 0 ? 1 : 0);
            if (distinct > bestScore) {
                bestScore = distinct;
                bestRow = r;
                bestStudent = student;
                bestName = name;
                bestHours = hours;
                bestCredit = credit;
            }
        }
        if (bestScore < 1) {
            return new TableMapping(0, null, null, null, null, "hour",
                    "未识别到表头，请在上传时补充说明（例如：学号在第9列，时长在第10列）", false);
        }
        String unit = "hour";
        if (bestHours >= 0 && bestRow >= 0) {
            String header = normalize(rows.get(bestRow).get(bestHours));
            if (header.contains("分钟") || header.contains("min")) {
                unit = "min";
            }
        }
        return new TableMapping(bestRow, none(bestStudent), none(bestName), none(bestHours), none(bestCredit),
                unit, "已按表头关键词识别", false);
    }

    private int findColumn(List<String> row, List<String> keys, boolean excludeSeq) {
        for (int c = 0; c < row.size(); c++) {
            String cell = normalize(row.get(c));
            if (cell.isBlank()) {
                continue;
            }
            boolean seqOnly = SEQ_KEYS.stream().anyMatch(cell::equals);
            if (excludeSeq && seqOnly && STUDENT_KEYS.stream().noneMatch(cell::contains)) {
                continue;
            }
            for (String key : keys) {
                if (cell.contains(key)) {
                    return c;
                }
            }
        }
        return -1;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\u00A0]", "")
                .replace("（", "(").replace("）", ")")
                .replace("：", ":").replace("／", "/");
    }

    private Integer none(int value) {
        return value < 0 ? null : value;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    /**
     * 识别结果。
     *
     * @param headerRow   表头行（0 基）
     * @param studentNoCol 学号列下标
     * @param nameCol      姓名列下标
     * @param hoursCol     时长列下标
     * @param creditCol    学分列下标
     * @param hoursUnit    hour / min
     * @param notes        识别说明
     * @param aiUsed       是否使用了模型识别
     */
    public record TableMapping(int headerRow, Integer studentNoCol, Integer nameCol,
                               Integer hoursCol, Integer creditCol, String hoursUnit,
                               String notes, boolean aiUsed) {

        public Map<String, String> columnLabels(List<List<String>> rows) {
            Map<String, String> labels = new LinkedHashMap<>();
            labels.put("studentNo", header(rows, headerRow, studentNoCol));
            labels.put("name", header(rows, headerRow, nameCol));
            labels.put("hours", header(rows, headerRow, hoursCol));
            labels.put("credit", header(rows, headerRow, creditCol));
            return labels;
        }

        private String header(List<List<String>> rows, int rowIndex, Integer col) {
            if (rowIndex < 0 || rowIndex >= rows.size() || col == null) {
                return null;
            }
            List<String> row = rows.get(rowIndex);
            return col < row.size() ? row.get(col) : null;
        }
    }
}
