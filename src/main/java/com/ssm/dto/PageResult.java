package com.ssm.dto;

import java.util.List;

public class PageResult<T> {
    public long total;
    public int page;
    public int size;
    public List<T> records;

    public PageResult(long total, int page, int size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records;
    }
}
