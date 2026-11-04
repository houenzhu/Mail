package com.zhe.mall.admin.service;

import com.zhe.mall.api.OrderApi;
import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 后台订单聚合服务。
 *
 * <p>订单数据全部在 {@code mall-order}（独立库），BFF 只做转发与错误码翻译，
 * 不做业务规则判断——订单状态机、归属校验都留在订单服务内，避免两份实现。
 *
 * <p>与商品/库存不同，这里**不做降级**：订单列表是后台的核心视图，
 * 拿不到数据时必须明确报错，不能返回一个"没有订单"的空列表来伪装成功。
 */
@Service
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private final OrderApi orderApi;

    public AdminOrderService(OrderApi orderApi) {
        this.orderApi = orderApi;
    }

    public PageResult<AdminOrderRow> page(AdminOrderPageQuery query) {
        AdminOrderPageQuery effective = query == null
                ? new AdminOrderPageQuery(null, null, null, null, null, 1, 10)
                : query;
        return unwrap(orderApi.adminPage(effective), "查询订单分页");
    }

    public AdminOrderDetail detail(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "orderNo 不能为空");
        }
        return unwrap(orderApi.adminDetail(orderNo.trim()), "查询订单详情");
    }

    /** 取 Result.data，业务失败转成异常（与 AdminProductService 同一口径）。 */
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
