package com.example.zhihuiqingtu.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页返回结构。
 * <p>
 * 当前端请求“分页列表”时，后端除了要返回当前页的数据，
 * 还需要告诉前端当前页码、每页条数、总记录数、总页数等信息，
 * 这样前端才能正确绘制分页器。
 *
 * @param <T> 当前分页列表中每一项的数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * 当前页码。
     * 这里统一按前端习惯从 0 开始计数。
     */
    private Integer pageNo;

    /**
     * 每页条数。
     */
    private Integer pageSize;

    /**
     * 满足条件的总记录数。
     */
    private Long totalElements;

    /**
     * 总页数。
     */
    private Integer totalPages;

    /**
     * 当前页的数据列表。
     */
    private List<T> records;

    /**
     * 把 MyBatis-Plus 返回的分页对象转换成项目统一的分页响应结构。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  分页数据类型
     * @return 项目自己的统一分页响应对象
     */
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
