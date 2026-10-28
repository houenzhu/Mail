package com.zhe.mall.order.dto;

import lombok.Data;

@Data
public class OrderPageQuery {

    private Long userId;

    private Integer status;

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    public int offset() {
        return (pageNo() - 1) * pageSize();
    }

    public int pageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int pageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
