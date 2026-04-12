package com.example.zhihuiqingtu.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 通用分页返回结构。
 *
 * @param <T> 列表元素类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * 当前页码，从 0 开始。
     */
    private Integer pageNo;

    /**
     * 每页数量。
     */
    private Integer pageSize;

    /**
     * 总记录数。
     */
    private Long totalElements;

    /**
     * 总页数。
     */
    private Integer totalPages;

    /**
     * 当前页数据。
     */
    private List<T> records;

    public static <T> PageResponse<T> from(IPage<T> page) {
        return PageResponse.<T>builder()
                .pageNo(Math.max((int) page.getCurrent() - 1, 0))
                .pageSize((int) page.getSize())
                .totalElements(page.getTotal())
                .totalPages((int) page.getPages())
                .records(page.getRecords())
                .build();
    }
}
