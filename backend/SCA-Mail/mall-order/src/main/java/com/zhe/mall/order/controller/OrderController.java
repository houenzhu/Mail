package com.zhe.mall.order.controller;

import com.zhe.mall.common.auth.CurrentUserId;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.order.dto.OrderCancelRequest;
import com.zhe.mall.order.dto.OrderCreateRequest;
import com.zhe.mall.order.dto.OrderCreateVO;
import com.zhe.mall.order.dto.OrderDTO;
import com.zhe.mall.order.dto.OrderDetailVO;
import com.zhe.mall.order.dto.OrderPageQuery;
import com.zhe.mall.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单对外接口。
 *
 * <p>用户身份一律来自网关注入的登录上下文（{@code @CurrentUserId}），不再信任请求参数里的 userId，
 * 避免越权（见需求文档 21.3）。
 */
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<OrderCreateVO> create(@Valid @RequestBody OrderCreateRequest request,
                                        @CurrentUserId Long userId) {
        return Result.success(orderService.create(request, userId));
    }

    @GetMapping("/page")
    public Result<PageResult<OrderDTO>> page(
            @CurrentUserId Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        OrderPageQuery query = new OrderPageQuery();
        query.setUserId(userId);
        query.setStatus(status);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.success(orderService.page(query));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderDetailVO> detail(@PathVariable("orderNo") String orderNo,
                                        @CurrentUserId Long userId) {
        return Result.success(orderService.detail(orderNo, userId));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<OrderDTO> cancel(@PathVariable("orderNo") String orderNo,
                                   @RequestBody(required = false) OrderCancelRequest request,
                                   @CurrentUserId Long userId) {
        String reason = request == null ? null : request.reason();
        return Result.success(orderService.cancel(orderNo, reason, userId));
    }
}
