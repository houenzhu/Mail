package com.zhe.mall.api.dto;

/**
 * 管理端商品分页查询入参（mall-admin → mall-product）。
 *
 * <p>与 C 端的差异（见计划文档 G-02）：
 * <ul>
 *   <li>{@code deleted} 可查已删除商品（C 端固定只查未删除）；</li>
 *   <li>{@code productId} 支持按 ID 精确查（后台按 ID 搜索比模糊匹配更常用）；</li>
 *   <li>关键字同时匹配商品名与卖点副标题（C 端只匹配名称）。</li>
 * </ul>
 * 分页字段沿用 pageNo/pageSize，越界由服务端兜底（pageSize 上限 100）。
 */
public record AdminProductPageQuery(
        String keyword,
        Long productId,
        Long categoryId,
        Long brandId,
        Integer status,
        Integer deleted,
        Integer pageNo,
        Integer pageSize) {

    public int safePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    public int offset() {
        return (safePageNo() - 1) * safePageSize();
    }
}
