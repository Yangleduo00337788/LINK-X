package com.linkx.server.common.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 管理端统一分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResultVO<T> {

    private List<T> items;
    private long page;
    private long size;
    private long total;

    public static <T> PageResultVO<T> of(List<T> items, long page, long size, long total) {
        return PageResultVO.<T>builder()
                .items(items != null ? items : Collections.emptyList())
                .page(page)
                .size(size)
                .total(total)
                .build();
    }

    public static <T> PageResultVO<T> empty(long page, long size) {
        return of(Collections.emptyList(), page, size, 0);
    }
}
