package com.zhe.mall.inventory.controller;

import com.zhe.mall.api.dto.DeductRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.AdjustStockRequest;
import com.zhe.mall.api.dto.InitStockRequest;
import com.zhe.mall.api.dto.InventoryBatchRequest;
import com.zhe.mall.api.dto.InventoryDTO;
import com.zhe.mall.api.dto.InventoryRecordPage;
import com.zhe.mall.api.dto.InventoryRecordPageQuery;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import jakarta.validation.Valid;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{skuId}")
    @RequireStaff
    public Result<InventoryDTO> get(@PathVariable("skuId") Long skuId) {
        return Result.success(inventoryService.get(skuId));
    }

    /**
     * 批量查询库存（管理端商品列表用，见计划文档 8.2）。
     *
     * <p>身份说明：本接口不在 {@code /internal/} 路径下，由网关做登录校验；
     * 服务端仍只信任携带内部令牌的身份头（直连服务伪造身份头无效）。
     */
    @PostMapping("/batch")
    @RequireStaff
    public Result<Map<Long, InventoryDTO>> batch(@RequestBody InventoryBatchRequest request) {
        return Result.success(inventoryService.getBatch(request == null ? null : request.skuIds()));
    }

    @PostMapping("/deduct")
    public Result<DeductResult> deduct(@RequestBody DeductRequest request) {
        return Result.success(inventoryService.deduct(request));
    }

    @PostMapping("/rollback")
    public Result<DeductResult> rollback(@RequestBody DeductRequest request) {
        return Result.success(inventoryService.rollback(request));
    }

    @PostMapping("/adjust")
    @RequireStaff(UserRole.OPERATOR)
    public Result<DeductResult> adjust(@Valid @RequestBody AdjustStockRequest request) {
        return Result.success(inventoryService.adjust(request));
    }

    @PostMapping("/record/page")
    @RequireStaff
    public Result<InventoryRecordPage> recordPage(@RequestBody InventoryRecordPageQuery query) {
        return Result.success(inventoryService.recordPage(query));
    }

    @PostMapping("/init")
    public Result<Void> init(@RequestBody InitStockRequest request) {
        inventoryService.init(request);
        return Result.success();
    }
}
