package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.service.AdminOrderService;
import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单管理接口（对外统一 {@code /api/admin/order/**}）。
 *
 * <p>鉴权与商品/库存一致：网关保证会话有效，BFF 控制器要求 staff，
 * 下游 {@code mall-order} 的 {@code /api/order/admin/**} 再校验一次 staff，形成双层边界。
 *
 * <p>当前只提供查询（分页 + 详情）；发货等写操作在后续切片里加，
 * 届时同样由下游 {@code @RequireStaff(OPERATOR)} 做操作级校验。
 */
@RestController
@RequestMapping("/api/admin/order")
@RequireStaff
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    /**
     * 后台订单分页。
     *
     * <p>用 POST + body：筛选条件多（订单号 / 买家 / 状态 / 时间区间）且大多可选，
     * body 比一长串 query 更清晰，也与商品分页的契约风格一致。
     */
    @PostMapping("/page")
    public Result<PageResult<AdminOrderRow>> page(
            @RequestBody(required = false) AdminOrderPageQuery query) {
        return Result.success(adminOrderService.page(query));
    }

    /** 后台订单详情：订单主体 + 明细 + 状态流转时间线。 */
    @GetMapping("/{orderNo}")
    public Result<AdminOrderDetail> detail(@PathVariable("orderNo") String orderNo) {
        return Result.success(adminOrderService.detail(orderNo));
    }
}
