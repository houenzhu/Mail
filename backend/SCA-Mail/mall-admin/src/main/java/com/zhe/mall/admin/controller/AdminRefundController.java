package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.service.AdminRefundService;
import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRow;
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
 * 后台退款管理接口（对外统一 {@code /api/admin/refund/**}）。
 *
 * <p>当前只提供查询；退款申请仍走 C 端 {@code POST /api/pay/refund}
 * （客服代客退款也复用该接口，身份由网关保证）。
 */
@RestController
@RequestMapping("/api/admin/refund")
@RequireStaff
public class AdminRefundController {

    private final AdminRefundService adminRefundService;

    public AdminRefundController(AdminRefundService adminRefundService) {
        this.adminRefundService = adminRefundService;
    }

    /** 退款单分页：退款单号 / 订单号 / 买家 / 状态 / 创建时间。 */
    @PostMapping("/page")
    public Result<PageResult<AdminRefundRow>> page(
            @RequestBody(required = false) AdminRefundPageQuery query) {
        return Result.success(adminRefundService.page(query));
    }

    /** 退款单详情。 */
    @GetMapping("/{refundNo}")
    public Result<AdminRefundDetail> detail(@PathVariable("refundNo") String refundNo) {
        return Result.success(adminRefundService.detail(refundNo));
    }
}
