package com.zhe.mall.api;

import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRow;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 支付服务对后台的契约（mall-admin → mall-pay）。
 *
 * <p>只放管理端视角的只读查询；C 端的支付/退款申请仍由 {@code PayController} 提供，
 * 两者不共用路径，避免管理端能力被 C 端令牌误用。
 */
@FeignClient(name = "mall-pay", path = "/api/pay")
public interface PayApi {

    /** 后台退款单分页：全量，支持退款单号 / 订单号 / 买家 / 状态 / 创建时间筛选。 */
    @PostMapping("/admin/refund/page")
    Result<PageResult<AdminRefundRow>> adminRefundPage(@RequestBody AdminRefundPageQuery query);

    /** 后台退款单详情。 */
    @GetMapping("/admin/refund/{refundNo}")
    Result<AdminRefundDetail> adminRefundDetail(@PathVariable("refundNo") String refundNo);
}
