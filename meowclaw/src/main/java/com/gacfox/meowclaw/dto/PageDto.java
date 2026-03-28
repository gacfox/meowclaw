package com.gacfox.meowclaw.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageDto<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;

    public static <T> PageDto<T> of(List<T> items, long total, int page, int pageSize) {
        PageDto<T> dto = new PageDto<>();
        dto.setItems(items);
        dto.setTotal(total);
        dto.setPage(page);
        dto.setPageSize(pageSize);
        dto.setTotalPages((int) Math.ceil((double) total / pageSize));
        return dto;
    }
}
