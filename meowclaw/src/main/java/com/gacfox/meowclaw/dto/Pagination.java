package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 泛型通用分页对象
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Pagination<T> implements Serializable {
    /**
     * 分页数据
     */
    private List<T> list = new ArrayList<>();
    /**
     * 总数据记录数
     */
    private int total = 0;
    /**
     * 总页数
     */
    private Integer totalPage;
    /**
     * 当前页码
     */
    private int current = 1;
    /**
     * 分页大小
     */
    private int pageSize = 10;

    /**
     * 将列表转换成分页对象
     *
     * @param list        列表
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @param <T>         响应实体类型
     * @return 分页对象
     */
    public static <T> Pagination<T> fromList(List<T> list, int currentPage, int pageSize) {
        if (list == null) {
            return null;
        }
        List<T> listPage = new ArrayList<>();
        int totalRows = list.size();
        int pageStart = currentPage == 1 ? 0 : (currentPage - 1) * pageSize;
        int pageEnd = Math.min(totalRows, currentPage * pageSize);
        if (totalRows > pageStart) {
            listPage = list.subList(pageStart, pageEnd);
        }
        int totalPages = (int) Math.ceil((double) totalRows / pageSize);
        return new Pagination<>(listPage, totalRows, totalPages, currentPage, pageSize);
    }
}
