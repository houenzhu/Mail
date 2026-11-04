package com.zhe.mall.api.dto;

/**
 * 订单按状态计数（一次 GROUP BY 返回全部状态，避免多次 count）。
 */
public record AdminOrderCountStat(Integer status, long total) {
}
