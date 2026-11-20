package com.example.zhihuiqingtu.admin.support;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 管理端分页参数边界处理。
 */
public final class AdminPages {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private AdminPages() {
    }

    /**
     * 把前端传入的页码/页大小规整为安全值，避免负数或一次拉取过多数据。
     *
     * @param pageNo   页码（从 0 开始）
     * @param pageSize 每页数量
     * @param <T>      分页元素类型
     * @return MyBatis-Plus 分页对象（页码从 1 开始）
     */
    public static <T> Page<T> of(Integer pageNo, Integer pageSize) {
        int safePageNo = pageNo == null ? 0 : Math.max(pageNo, 0);
        int safePageSize = pageSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return new Page<>(safePageNo + 1L, safePageSize);
    }
}
