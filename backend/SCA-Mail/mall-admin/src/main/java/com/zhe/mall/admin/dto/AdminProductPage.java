package com.zhe.mall.admin.dto;

import java.util.List;

/**
 * 管理端商品分页结果：分页数据 + 状态角标一次返回。
 *
 * <p>把 Tab 角标放进同一个响应，是为了让前端"打开列表"只发一个请求
 * （否则要么发多个 count 请求，要么把角标做成异步二次刷新导致数字跳动）。
 */
public record AdminProductPage(
        long total,
        int pageNo,
        int pageSize,
        List<AdminProductRow> records,
        Counts statusCounts) {

    /** Tab 角标：全部 / 出售中 / 仓库中 / 已删除。 */
    public record Counts(long all, long on, long off, long deleted) {
    }
}
