package com.zhe.mall.pay.controller;

import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRow;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.pay.service.AdminRefundQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台退款查询接口（mall-admin 经 Feign 调用）。
 *
 * <p>路径放在 {@code /api/pay/admin/**}：网关的公开白名单只覆盖 C 端支付路径，
 * 这里必须带有效会话；服务端再以 {@code @RequireStaff} 校验角色（直连也 fail-closed）。
 */
@RestController
@RequestMapping("/api/pay/admin/refund")
@RequireStaff
public class AdminRefundController {

    private final AdminRefundQueryService adminRefundQueryService;

    public AdminRefundController(AdminRefundQueryService adminRefundQueryService) {
        this.adminRefundQueryService = adminRefundQueryService;
    }

    /** 后台退款单分页。 */
    @PostMapping("/page")
    public Result<PageResult<AdminRefundRow>> page(
            @RequestBody(required = false) AdminRefundPageQuery query) {
        return Result.success(adminRefundQueryService.page(query));
    }

    /** 后台退款单详情。 */
    @GetMapping("/{refundNo}")
    public Result<AdminRefundDetail> detail(@PathVariable("refundNo") String refundNo) {
        return Result.success(adminRefundQueryService.detail(refundNo));
    }
}
