package com.zhe.mall.api;

import com.zhe.mall.api.dto.DeductRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.InventoryBatchRequest;
import com.zhe.mall.api.dto.InventoryDTO;
import com.zhe.mall.api.dto.InitStockRequest;
import com.zhe.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "mall-inventory", path = "/api/inventory")
public interface InventoryApi {

    @GetMapping("/{skuId}")
    Result<InventoryDTO> getBySkuId(@PathVariable("skuId") Long skuId);

    /**
     * 批量查询库存（管理端商品列表聚合，见计划文档 8.2）。
     * 返回 map 而不是 list：调用方按 skuId 直接取值，避免再遍历一次。
     */
    @PostMapping("/batch")
    Result<Map<Long, InventoryDTO>> getBatch(@RequestBody InventoryBatchRequest request);

    @PostMapping("/deduct")
    Result<DeductResult> deduct(@RequestBody DeductRequest request);

    @PostMapping("/rollback")
    Result<DeductResult> rollback(@RequestBody DeductRequest request);

    @PostMapping("/init")
    Result<Void> init(@RequestBody InitStockRequest request);
}
