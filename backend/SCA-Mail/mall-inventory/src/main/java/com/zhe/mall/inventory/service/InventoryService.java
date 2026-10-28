package com.zhe.mall.inventory.service;

import com.zhe.mall.api.dto.DeductRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.InitStockRequest;
import com.zhe.mall.api.dto.InventoryDTO;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.common.util.SnowflakeIdGenerator;
import com.zhe.mall.inventory.entity.Inventory;
import com.zhe.mall.inventory.entity.InventoryRecord;
import com.zhe.mall.inventory.enums.InventoryRecordStatus;
import com.zhe.mall.inventory.enums.InventoryRecordType;
import com.zhe.mall.inventory.mapper.InventoryMapper;
import com.zhe.mall.inventory.mapper.InventoryRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private static final int MIN_WARN_THRESHOLD = 10;

    private final InventoryMapper inventoryMapper;

    private final InventoryRecordMapper inventoryRecordMapper;

    public InventoryService(InventoryMapper inventoryMapper, InventoryRecordMapper inventoryRecordMapper) {
        this.inventoryMapper = inventoryMapper;
        this.inventoryRecordMapper = inventoryRecordMapper;
    }

    public InventoryDTO get(Long skuId) {
        Inventory inventory = inventoryMapper.selectById(skuId);
        if (inventory == null) {
            throw new BusinessException(ResultCode.INVENTORY_NOT_FOUND, "库存记录不存在: " + skuId);
        }
        return toDTO(inventory);
    }

    /**
     * 批量查询库存（管理端商品列表用）。
     *
     * <p>与 {@link #get(Long)} 不同：查不到的 SKU **不抛异常**，只是不出现在结果里——
     * 列表场景下"某个 SKU 还没初始化库存"是正常状态，不该让整页失败。
     */
    public Map<Long, InventoryDTO> getBatch(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = skuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return inventoryMapper.selectByIds(distinct).stream()
                .collect(Collectors.toMap(Inventory::getSkuId, this::toDTO, (a, b) -> a));
    }

    @Transactional(rollbackFor = Exception.class)
    public DeductResult deduct(DeductRequest request) {
        requireRequest(request);
        InventoryRecord claim = newClaim(request, InventoryRecordType.DEDUCT);
        if (!tryClaim(claim)) {
            return replay(request, InventoryRecordType.DEDUCT);
        }

        int affected = inventoryMapper.deduct(request.skuId(), request.qty());
        if (affected == 0) {
            Inventory current = inventoryMapper.selectById(request.skuId());
            String message = current == null
                    ? "库存记录不存在: " + request.skuId()
                    : "库存不足，当前可售: " + current.getAvailable();
            ResultCode resultCode = current == null ? ResultCode.INVENTORY_NOT_FOUND : ResultCode.STOCK_NOT_ENOUGH;
            inventoryRecordMapper.updateStatus(claim.getId(), InventoryRecordStatus.FAILED.code(), message);
            log.warn("库存扣减失败: orderNo={}, skuId={}, qty={}, reason={}",
                    request.orderNo(), request.skuId(), request.qty(), message);
            return DeductResult.fail(resultCode, message);
        }

        inventoryRecordMapper.updateStatus(claim.getId(), InventoryRecordStatus.SUCCESS.code(), null);
        Inventory after = inventoryMapper.selectById(request.skuId());
        Integer remaining = after == null ? null : after.getAvailable();
        log.info("库存扣减成功: orderNo={}, skuId={}, qty={}, remaining={}",
                request.orderNo(), request.skuId(), request.qty(), remaining);
        return DeductResult.ok(remaining);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeductResult rollback(DeductRequest request) {
        requireRequest(request);
        InventoryRecord claim = newClaim(request, InventoryRecordType.ROLLBACK);
        if (!tryClaim(claim)) {
            return replay(request, InventoryRecordType.ROLLBACK);
        }

        int affected = inventoryMapper.rollback(request.skuId(), request.qty());
        if (affected == 0) {
            String message = "回滚失败：库存记录不存在或可售库存将超过总库存";
            inventoryRecordMapper.updateStatus(claim.getId(), InventoryRecordStatus.FAILED.code(), message);
            log.warn("库存回滚失败: orderNo={}, skuId={}, qty={}", request.orderNo(), request.skuId(), request.qty());
            return DeductResult.fail(ResultCode.ROLLBACK_FAILED, message);
        }

        inventoryRecordMapper.updateStatus(claim.getId(), InventoryRecordStatus.SUCCESS.code(), null);
        Inventory after = inventoryMapper.selectById(request.skuId());
        Integer remaining = after == null ? null : after.getAvailable();
        log.info("库存回滚成功: orderNo={}, skuId={}, qty={}, remaining={}",
                request.orderNo(), request.skuId(), request.qty(), remaining);
        return DeductResult.rollbackOk(remaining);
    }

    @Transactional(rollbackFor = Exception.class)
    public void init(InitStockRequest request) {
        if (request == null || request.skuId() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "skuId 不能为空");
        }
        if (request.qty() == null || request.qty() < 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "初始库存不能为负");
        }
        int warnThreshold = Math.max(MIN_WARN_THRESHOLD, request.qty() / 10);
        inventoryMapper.init(request.skuId(), request.qty(), warnThreshold);
        log.info("库存初始化: skuId={}, qty={}", request.skuId(), request.qty());
    }

    private boolean tryClaim(InventoryRecord claim) {
        try {
            inventoryRecordMapper.insert(claim);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private DeductResult replay(DeductRequest request, InventoryRecordType type) {
        InventoryRecord existing = inventoryRecordMapper.selectByUniqueKey(request.orderNo(), request.skuId(), type.code());
        if (existing == null) {
            throw new BusinessException(ResultCode.DUPLICATE_REQUEST);
        }
        if (Objects.equals(existing.getStatus(), InventoryRecordStatus.SUCCESS.code())) {
            Inventory inventory = inventoryMapper.selectById(request.skuId());
            log.info("幂等命中，返回首次结果: orderNo={}, skuId={}, type={}", request.orderNo(), request.skuId(), type.label());
            return DeductResult.ok(inventory == null ? null : inventory.getAvailable());
        }
        if (Objects.equals(existing.getStatus(), InventoryRecordStatus.PROCESSING.code())) {
            throw new BusinessException(ResultCode.DUPLICATE_REQUEST, "上一次请求正在处理中，请勿重复提交");
        }
        return DeductResult.fail(ResultCode.BUSINESS_ERROR,
                existing.getRemark() == null ? "上一次操作失败" : existing.getRemark());
    }

    private InventoryRecord newClaim(DeductRequest request, InventoryRecordType type) {
        InventoryRecord record = new InventoryRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setSkuId(request.skuId());
        record.setOrderNo(request.orderNo());
        record.setType(type.code());
        record.setQty(request.qty());
        record.setStatus(InventoryRecordStatus.PROCESSING.code());
        return record;
    }

    private void requireRequest(DeductRequest request) {
        if (request == null || request.orderNo() == null || request.orderNo().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "orderNo 不能为空");
        }
        if (request.skuId() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "skuId 不能为空");
        }
        if (request.qty() == null || request.qty() <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "扣减数量必须大于 0");
        }
    }

    private InventoryDTO toDTO(Inventory inventory) {
        return new InventoryDTO(inventory.getSkuId(), inventory.getTotal(), inventory.getLocked(), inventory.getAvailable());
    }
}
