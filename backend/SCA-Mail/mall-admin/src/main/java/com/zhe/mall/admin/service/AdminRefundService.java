package com.zhe.mall.admin.service;

import com.zhe.mall.api.PayApi;
import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRejectRequest;
import com.zhe.mall.api.dto.AdminRefundRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 后台退款聚合服务。
 *
 * <p>与订单 BFF 一致：只转发与翻译错误码，不做降级。退款是资金视图，
 * 下游不可用时返回空列表会让客服误以为"没有退款单"，必须明确报错。
 */
@Service
public class AdminRefundService {

    private static final Logger log = LoggerFactory.getLogger(AdminRefundService.class);

    private final PayApi payApi;

    public AdminRefundService(PayApi payApi) {
        this.payApi = payApi;
    }

    public PageResult<AdminRefundRow> page(AdminRefundPageQuery query) {
        AdminRefundPageQuery effective = query == null
                ? new AdminRefundPageQuery(null, null, null, null, null, null, 1, 10)
                : query;
        return unwrap(payApi.adminRefundPage(effective), "查询退款单分页");
    }

    public AdminRefundDetail detail(String refundNo) {
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "refundNo 不能为空");
        }
        return unwrap(payApi.adminRefundDetail(refundNo.trim()), "查询退款单详情");
    }

    /**
     * 审核通过（0 → 10 → 20）。
     *
     * <p>BFF 不判断"能不能审"：状态机、角色与渠道调用都在 mall-pay，
     * 这里只翻译错误码，避免两套退款规则。
     */
    public AdminRefundDetail approve(String refundNo) {
        String effective = requireRefundNo(refundNo);
        return unwrap(payApi.adminRefundApprove(effective), "审核通过退款");
    }

    /** 审核驳回（0 → 30），原因随请求下传，空值由下游补默认文案。 */
    public AdminRefundDetail reject(String refundNo, AdminRefundRejectRequest request) {
        String effective = requireRefundNo(refundNo);
        AdminRefundRejectRequest body = request == null ? new AdminRefundRejectRequest(null) : request;
        return unwrap(payApi.adminRefundReject(effective, body), "驳回退款");
    }

    private String requireRefundNo(String refundNo) {
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "refundNo 不能为空");
        }
        return refundNo.trim();
    }

    private <T> T unwrap(Result<T> result, String action) {
        if (result == null || result.code() != ResultCode.SUCCESS.getCode()) {
            String message = result == null ? "下游无响应" : result.message();
            log.warn("{}失败: code={}, message={}", action,
                    result == null ? null : result.code(), message);
            throw new BusinessException(
                    result == null ? ResultCode.DOWNSTREAM_UNAVAILABLE.getCode() : result.code(),
                    action + "失败：" + message);
        }
        return result.data();
    }
}
