package com.zhe.mall.common.result;

import java.util.List;

public record PageResult<T>(long total, int pageNo, int pageSize, List<T> list) {

    public static <T> PageResult<T> of(long total, int pageNo, int pageSize, List<T> list) {
        return new PageResult<>(total, pageNo, pageSize, list);
    }

    public static <T> PageResult<T> empty(int pageNo, int pageSize) {
        return new PageResult<>(0L, pageNo, pageSize, List.of());
    }
}
