package com.example.zhihuiqingtu.ai.service;

import com.example.zhihuiqingtu.ai.client.SpringAiChatGateway;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 表格列识别的规则降级测试（AI 不可用时）。
 */
class AiTableMappingServiceTests {

    private final SpringAiChatGateway gateway = mock(SpringAiChatGateway.class);
    private final AiTableMappingService service = new AiTableMappingService(gateway, new ObjectMapper());

    @Test
    void detectsTitleHeaderAndColumnsForVolunteerSheet() {
        when(gateway.isAvailable()).thenReturn(false);
        List<List<String>> rows = List.of(
                List.of("协助第十四届学生运动会志愿服务活动时长统计表"),
                List.of("序号", "姓名", "性别", "学院", "年级", "专业班级", "电话", "QQ", "学号", "时长/h"),
                List.of("1", "张三", "男", "化学学院", "21级", "化学221班", "158", "303", "2207030125", "95"),
                List.of("2", "李四", "女", "教育学院", "23级", "小教241班", "185", "359", "2207030230", "34.5")
        );

        AiTableMappingService.TableMapping mapping = service.analyze(rows, null, "hours");

        assertEquals(1, mapping.headerRow());
        assertEquals(8, mapping.studentNoCol());
        assertEquals(1, mapping.nameCol());
        assertEquals(9, mapping.hoursCol());
        assertNull(mapping.creditCol());
        assertEquals("hour", mapping.hoursUnit());
        assertFalse(mapping.aiUsed());
        assertNotNull(mapping.columnLabels(rows).get("studentNo"));
    }

    @Test
    void detectsMinuteUnitAndDoesNotTreatSeqAsStudentNo() {
        when(gateway.isAvailable()).thenReturn(false);
        List<List<String>> rows = List.of(
                List.of("序号", "姓名", "学号", "志愿时长（分钟）"),
                List.of("1", "王五", "S001", "90")
        );

        AiTableMappingService.TableMapping mapping = service.analyze(rows, null, "hours");

        assertEquals(0, mapping.headerRow());
        assertEquals(2, mapping.studentNoCol());
        assertEquals(3, mapping.hoursCol());
        assertEquals("min", mapping.hoursUnit());
    }

    @Test
    void detectsCreditColumn() {
        when(gateway.isAvailable()).thenReturn(false);
        List<List<String>> rows = List.of(
                List.of("姓名", "学号", "学分"),
                List.of("赵六", "S002", "1.5")
        );

        AiTableMappingService.TableMapping mapping = service.analyze(rows, null, "credit");

        assertEquals(0, mapping.headerRow());
        assertEquals(1, mapping.studentNoCol());
        assertEquals(2, mapping.creditCol());
        assertNull(mapping.hoursCol());
    }
}
