package com.example.zhihuiqingtu.admin.award.support;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.exception.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把上传的表格读成纯文本二维数组。
 *
 * <p>支持 xlsx / xls / csv，限制 5MB、最多 {@value #MAX_ROWS} 行（含表头与标题行），
 * 防止一个超大文件把内存和 AI 上下文撑爆。
 */
@Component
public class ExcelTableReader {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 1000;

    /**
     * 读取表格第一个工作表。
     *
     * @param file 上传文件
     * @return 行数组；每个元素是一行的单元格文本（已去掉行尾空单元格）
     */
    public List<List<String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先选择要导入的表格文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "表格文件不能超过5MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return readCsv(file);
            }
            if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 .xlsx / .xls / .csv 格式的表格");
            }
            return readWorkbook(file);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "表格解析失败，请检查文件格式");
        }
    }

    private List<List<String>> readWorkbook(MultipartFile file) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "表格中没有工作表");
            }
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<List<String>> rows = new ArrayList<>();
            int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS - 1);
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                List<String> values = new ArrayList<>();
                if (row != null) {
                    int lastCell = Math.max(row.getLastCellNum(), 0);
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        values.add(cellText(cell, formatter));
                    }
                }
                trimTrailingBlanks(values);
                rows.add(values);
            }
            trimTrailingEmptyRows(rows);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "表格内容为空");
            }
            return rows;
        }
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            // 学号等长数字：DataFormatter 可能输出科学计数法，这里统一转成无指数纯数字
            double value = cell.getNumericCellValue();
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return BigDecimal.valueOf(value).toBigInteger().toString();
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        String text = formatter.formatCellValue(cell);
        return text == null ? "" : text.trim();
    }

    private List<List<String>> readCsv(MultipartFile file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        byte[] bytes = file.getBytes();
        int offset = (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) ? 3 : 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes, offset, bytes.length - offset), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && rows.size() < MAX_ROWS) {
                rows.add(parseCsvLine(line));
            }
        }
        trimTrailingEmptyRows(rows);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "表格内容为空");
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        trimTrailingBlanks(values);
        return values;
    }

    private void trimTrailingBlanks(List<String> values) {
        while (!values.isEmpty() && values.get(values.size() - 1).isBlank()) {
            values.remove(values.size() - 1);
        }
    }

    private void trimTrailingEmptyRows(List<List<String>> rows) {
        while (!rows.isEmpty() && rows.get(rows.size() - 1).stream().allMatch(String::isBlank)) {
            rows.remove(rows.size() - 1);
        }
    }
}
