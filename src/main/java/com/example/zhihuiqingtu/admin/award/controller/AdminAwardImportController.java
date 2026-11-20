package com.example.zhihuiqingtu.admin.award.controller;

import com.example.zhihuiqingtu.admin.award.dto.ApplyAwardImportRequest;
import com.example.zhihuiqingtu.admin.award.service.AdminAwardImportService;
import com.example.zhihuiqingtu.admin.award.vo.AwardImportApplyResultVO;
import com.example.zhihuiqingtu.admin.award.vo.AwardImportPreviewVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.zhihuiqingtu.config.SecurityConfig;

/**
 * 管理端 AI 批量导入发放。
 *
 * <p>两阶段：analyze 只解析与预览（不写库），apply 才写库并进入待审核。
 * URL 层已由 SecurityConfig 限制为 ADMIN/STAFF，服务层再校验活动发布者/管理员。
 */
@RestController
@RequestMapping("/api/admin/activities/{activityId}/award-import")
@RequiredArgsConstructor
public class AdminAwardImportController {

    private final AdminAwardImportService importService;

    /**
     * 上传表格，AI 识别表头与列并返回匹配预览。
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AwardImportPreviewVO> analyze(
            @PathVariable Long activityId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "instruction", required = false) String instruction) {
        return ApiResponse.success(importService.preview(activityId, file, instruction));
    }

    /**
     * 保存预览中勾选的记录（统一进入待管理员审核）。
     */
    @PostMapping("/apply")
    public ApiResponse<AwardImportApplyResultVO> apply(
            @PathVariable Long activityId,
            @Valid @RequestBody ApplyAwardImportRequest request) {
        AwardImportApplyResultVO result = importService.apply(activityId, request);
        return ApiResponse.success(result.getMessage(), result);
    }
}
