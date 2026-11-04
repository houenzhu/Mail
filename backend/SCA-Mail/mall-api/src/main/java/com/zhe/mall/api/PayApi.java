package com.zhe.mall.api;

import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRejectRequest;
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

    /**
     * 待处理退款单数（refund.status=10）。
     *
     * <p>看板概览用：退款是资金动作，数字必须来自 mall-pay 的真实表，
     * 不能用订单状态反推（订单 60 只表示"退款中订单"，与退款单数量不是一回事）。
     */
    @GetMapping("/admin/refund/pending-count")
    Result<Long> adminRefundPendingCount();

    /**
     * 审核通过：待审核(0) → 退款中(10) → 渠道退款 → 成功(20)。
     *
     * <p>返回审核后的完整退款单（含审核人 / 审核时间），BFF 不再单独回读。
     */
    @PostMapping("/admin/refund/{refundNo}/approve")
    Result<AdminRefundDetail> adminRefundApprove(@PathVariable("refundNo") String refundNo);

    /** 审核驳回：待审核(0) → 失败(30)，原因落 fail_reason，释放幂等键。 */
    @PostMapping("/admin/refund/{refundNo}/reject")
    Result<AdminRefundDetail> adminRefundReject(@PathVariable("refundNo") String refundNo,
                                                @RequestBody AdminRefundRejectRequest request);
}
