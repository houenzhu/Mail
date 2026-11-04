package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.dto.AdminInventoryRecordPage;
import com.zhe.mall.admin.service.AdminProductService;
import com.zhe.mall.api.dto.AdjustStockRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.InventoryDTO;
import com.zhe.mall.api.dto.InventoryRecordPageQuery;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台库存管理 BFF。 */
@RestController
@RequestMapping("/api/admin/inventory")
@RequireStaff
public class AdminInventoryController {

    private final AdminProductService adminProductService;

    public AdminInventoryController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @GetMapping("/sku/{skuId}")
    public Result<InventoryDTO> inventory(@PathVariable Long skuId) {
        return Result.success(adminProductService.inventory(skuId));
    }

    @PostMapping("/adjust")
    @RequireStaff(UserRole.OPERATOR)
    public Result<DeductResult> adjust(@Valid @RequestBody AdjustStockRequest request) {
        return Result.success(adminProductService.adjustInventory(request));
    }

    @PostMapping("/record/page")
    public Result<AdminInventoryRecordPage> recordPage(@RequestBody InventoryRecordPageQuery query) {
        return Result.success(adminProductService.inventoryRecords(query));
    }
}
