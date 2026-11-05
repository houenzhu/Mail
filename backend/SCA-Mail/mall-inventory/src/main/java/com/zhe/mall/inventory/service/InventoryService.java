package com.zhe.mall.inventory.service;

import com.zhe.mall.api.dto.DeductRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.AdjustStockRequest;
import com.zhe.mall.api.dto.AdminInventoryStat;
import com.zhe.mall.api.dto.InitStockRequest;
import com.zhe.mall.api.dto.InventoryDTO;
import com.zhe.mall.api.dto.InventoryRecordDTO;
import com.zhe.mall.api.dto.InventoryRecordPage;
import com.zhe.mall.api.dto.InventoryRecordPageQuery;
import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
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

    /** 看板库存域统计（售罄率 / 低库存）。 */
    public AdminInventoryStat stat() {
        AdminInventoryStat stat = inventoryMapper.selectStat();
        // 空表时 SQL 已用 coalesce 兜底，这里再保一层，避免映射异常时把看板整页打挂
        return stat == null ? new AdminInventoryStat(0, 0, 0, 0) : stat;
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

    /** C 端批量可售库存：只暴露 available，缺少库存记录的 SKU 不出现在结果中。 */
    public Map<Long, Integer> getAvailableBatch(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = skuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return inventoryMapper.selectByIds(distinct).stream()
                .collect(Collectors.toMap(Inventory::getSkuId, Inventory::getAvailable, (a, b) -> a));
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

    /**
     * 管理端手工调整库存。请求 ID 作为流水幂等键，调整方向进入 type，
     * 这样同一个请求重放不会重复改库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeductResult adjust(AdjustStockRequest request) {
        if (request == null || request.requestId() == null || request.requestId().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "requestId 不能为空");
        }
        if (request.requestId().length() > 32) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "requestId 长度不能超过 32");
        }
        if (request.skuId() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "skuId 不能为空");
        }
        if (request.skuId() <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "skuId 必须大于 0");
        }
        if (request.delta() == null || request.delta() == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "库存调整数量不能为 0");
        }
        if (request.delta() == Integer.MIN_VALUE) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "库存调整数量超出范围");
        }
        if (request.remark() != null && request.remark().length() > 255) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "备注长度不能超过 255");
        }

        InventoryRecordType type = request.delta() > 0
                ? InventoryRecordType.ADJUST_IN : InventoryRecordType.ADJUST_OUT;
        InventoryRecord claim = new InventoryRecord();
        claim.setId(SnowflakeIdGenerator.nextId());
        claim.setSkuId(request.skuId());
        claim.setOrderNo(request.requestId());
        claim.setType(type.code());
        claim.setQty(Math.abs(request.delta()));
        claim.setStatus(InventoryRecordStatus.PROCESSING.code());
        claim.setRemark(request.remark());
        CurrentUser operator = UserContext.get();
        if (operator == null || operator.userId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!operator.hasRole("OPERATOR")) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        claim.setOperatorId(operator.userId());
        claim.setOperatorName(operator.username());
        if (!tryClaim(claim)) {
            return replayAdjust(request, type);
        }

        int affected = inventoryMapper.adjust(request.skuId(), request.delta());
        if (affected == 0) {
            Inventory current = inventoryMapper.selectById(request.skuId());
            ResultCode code = current == null ? ResultCode.INVENTORY_NOT_FOUND : ResultCode.STOCK_ADJUST_INVALID;
            String message = current == null
                    ? "库存记录不存在: " + request.skuId()
                    : "调整后库存不能低于已锁定库存或小于 0";
            inventoryRecordMapper.updateStatus(claim.getId(), InventoryRecordStatus.FAILED.code(), message);
            return DeductResult.fail(code, message);
        }

        inventoryRecordMapper.updateStatus(claim.getId(), InventoryRecordStatus.SUCCESS.code(), request.remark());
        Inventory after = inventoryMapper.selectById(request.skuId());
        return DeductResult.ok(after == null ? null : after.getAvailable());
    }

    public InventoryRecordPage recordPage(InventoryRecordPageQuery query) {
        InventoryRecordPageQuery safe = query == null
                ? new InventoryRecordPageQuery(null, null, 1, 10) : query;
        if (safe.skuId() != null && safe.skuId() <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "skuId 必须大于 0");
        }
        if (safe.type() != null && (safe.type() < 1 || safe.type() > 6)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "库存流水类型必须在 1 到 6 之间");
        }
        int pageNo = safe.safePageNo();
        int pageSize = safe.safePageSize();
        List<InventoryRecord> records = inventoryRecordMapper.selectPage(
                safe.skuId(), safe.type(), safe.offset(), pageSize);
        List<InventoryRecordDTO> result = records.stream()
                .map(record -> new InventoryRecordDTO(record.getId(), record.getSkuId(), record.getOrderNo(),
                        record.getType(), record.getQty(), record.getStatus(), record.getRemark(),
                        record.getOperatorId(), record.getOperatorName(), record.getCreatedAt()))
                .toList();
        return new InventoryRecordPage(inventoryRecordMapper.countPage(safe.skuId(), safe.type()),
                pageNo, pageSize, result);
    }

    private DeductResult replayAdjust(AdjustStockRequest request, InventoryRecordType type) {
        InventoryRecord existing = inventoryRecordMapper.selectByUniqueKey(
                request.requestId(), request.skuId(), type.code());
        if (existing == null) {
            throw new BusinessException(ResultCode.DUPLICATE_REQUEST);
        }
        CurrentUser operator = UserContext.get();
        if (!Objects.equals(existing.getQty(), Math.abs(request.delta()))
                || !Objects.equals(existing.getOperatorId(), operator == null ? null : operator.userId())) {
            throw new BusinessException(ResultCode.DUPLICATE_REQUEST, "requestId 已用于不同的库存调整内容");
        }
        if (Objects.equals(existing.getStatus(), InventoryRecordStatus.SUCCESS.code())) {
            Inventory current = inventoryMapper.selectById(request.skuId());
            return DeductResult.ok(current == null ? null : current.getAvailable());
        }
        if (Objects.equals(existing.getStatus(), InventoryRecordStatus.PROCESSING.code())) {
            throw new BusinessException(ResultCode.DUPLICATE_REQUEST, "上一次调整正在处理中，请勿重复提交");
        }
        return DeductResult.fail(ResultCode.STOCK_ADJUST_INVALID,
                existing.getRemark() == null ? "上一次库存调整失败" : existing.getRemark());
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
