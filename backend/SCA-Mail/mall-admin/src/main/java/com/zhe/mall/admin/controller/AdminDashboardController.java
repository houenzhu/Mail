package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.service.AdminDashboardService;
import com.zhe.mall.api.dto.AdminDashboard;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台数据看板接口（UC-ADMIN-03）。
 *
 * <p>单接口返回概览 + 趋势 + 库存统计，前端一次请求即可渲染整页，
 * 避免看板打开时并发打三四个请求（每个都要过网关鉴权与 BFF）。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequireStaff
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * @param days 趋势天数，默认 7，服务端收敛到 1~90
     */
    @GetMapping
    public Result<AdminDashboard> dashboard(
            @RequestParam(value = "days", required = false) Integer days) {
        return Result.success(adminDashboardService.load(days));
    }
}
