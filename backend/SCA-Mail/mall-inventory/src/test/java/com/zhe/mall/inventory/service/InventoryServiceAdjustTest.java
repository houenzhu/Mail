package com.zhe.mall.inventory.service;

import com.zhe.mall.api.dto.AdjustStockRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.InventoryRecordPage;
import com.zhe.mall.api.dto.InventoryRecordPageQuery;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.inventory.controller.InventoryController;
import com.zhe.mall.inventory.entity.Inventory;
import com.zhe.mall.inventory.entity.InventoryRecord;
import com.zhe.mall.inventory.mapper.InventoryMapper;
import com.zhe.mall.inventory.mapper.InventoryRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class InventoryServiceAdjustTest {

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private InventoryRecordMapper inventoryRecordMapper;

    @InjectMocks
    private InventoryService service;

    @BeforeEach
    void setOperator() {
        UserContext.set(new CurrentUser(9001L, "operator01", List.of(UserRole.OPERATOR.name())));
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void adjustIncreaseWritesTypeFiveAndReturnsRemaining() {
        Inventory after = inventory(100, 0, 120);
        when(inventoryMapper.adjust(1001L, 20)).thenReturn(1);
        when(inventoryMapper.selectById(1001L)).thenReturn(after);

        DeductResult result = service.adjust(new AdjustStockRequest("req-1", 1001L, 20, "补货"));

        assertThat(result.success()).isTrue();
        assertThat(result.remaining()).isEqualTo(120);
        ArgumentCaptor<InventoryRecord> captor = ArgumentCaptor.forClass(InventoryRecord.class);
        verify(inventoryRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(5);
        assertThat(captor.getValue().getQty()).isEqualTo(20);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(9001L);
        assertThat(captor.getValue().getOperatorName()).isEqualTo("operator01");
        verify(inventoryMapper).adjust(1001L, 20);
    }

    @Test
    void adjustDecreaseBelowLockedStockReturnsBusinessFailure() {
        Inventory current = inventory(100, 80, 20);
        when(inventoryMapper.adjust(1001L, -30)).thenReturn(0);
        when(inventoryMapper.selectById(1001L)).thenReturn(current);

        DeductResult result = service.adjust(new AdjustStockRequest("req-2", 1001L, -30, "盘点扣减"));

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.STOCK_ADJUST_INVALID.getCode());
        verify(inventoryRecordMapper).updateStatus(any(), any(), any());
    }

    @Test
    void duplicateSuccessfulAdjustmentDoesNotUpdateInventoryAgain() {
        InventoryRecord existing = new InventoryRecord();
        existing.setStatus(1);
        existing.setQty(20);
        existing.setRemark("重放");
        existing.setOperatorId(9001L);
        when(inventoryMapper.selectById(1001L)).thenReturn(inventory(120, 0, 120));
        doThrow(new DuplicateKeyException("duplicate")).when(inventoryRecordMapper).insert(any());
        when(inventoryRecordMapper.selectByUniqueKey("req-3", 1001L, 5)).thenReturn(existing);

        DeductResult result = service.adjust(new AdjustStockRequest("req-3", 1001L, 20, "重放"));

        assertThat(result.success()).isTrue();
        assertThat(result.remaining()).isEqualTo(120);
        verify(inventoryMapper).selectById(1001L);
    }

    @Test
    void reusedRequestIdWithDifferentQuantityIsRejected() {
        InventoryRecord existing = new InventoryRecord();
        existing.setStatus(1);
        existing.setQty(10);
        existing.setRemark("补货");
        existing.setOperatorId(9001L);
        doThrow(new DuplicateKeyException("duplicate")).when(inventoryRecordMapper).insert(any());
        when(inventoryRecordMapper.selectByUniqueKey("req-5", 1001L, 5)).thenReturn(existing);

        assertThatThrownBy(() -> service.adjust(new AdjustStockRequest("req-5", 1001L, 20, "补货")))
                .isInstanceOf(com.zhe.mall.common.exception.BusinessException.class)
                .hasMessageContaining("不同的库存调整内容");
    }

    @Test
    void inventoryControllerProtectsManagementEndpoints() throws Exception {
        RequireStaff singleRead = InventoryController.class.getDeclaredMethod("get", Long.class)
                .getAnnotation(RequireStaff.class);
        RequireStaff batchRead = InventoryController.class.getDeclaredMethod("batch",
                com.zhe.mall.api.dto.InventoryBatchRequest.class).getAnnotation(RequireStaff.class);
        RequireStaff read = InventoryController.class.getDeclaredMethod("recordPage", InventoryRecordPageQuery.class)
                .getAnnotation(RequireStaff.class);
        RequireStaff write = InventoryController.class.getDeclaredMethod("adjust", AdjustStockRequest.class)
                .getAnnotation(RequireStaff.class);

        assertThat(singleRead).isNotNull();
        assertThat(batchRead).isNotNull();
        assertThat(read).isNotNull();
        assertThat(write).isNotNull();
        assertThat(write.value()).containsExactly(UserRole.OPERATOR);
    }

    @Test
    void recordPageMapsRowsAndUsesSafePaging() {
        InventoryRecord record = new InventoryRecord();
        record.setId(1L);
        record.setSkuId(1001L);
        record.setOrderNo("req-4");
        record.setType(5);
        record.setQty(10);
        record.setStatus(1);
        when(inventoryRecordMapper.selectPage(null, 5, 0, 10)).thenReturn(List.of(record));
        when(inventoryRecordMapper.countPage(null, 5)).thenReturn(1L);

        InventoryRecordPage page = service.recordPage(new InventoryRecordPageQuery(null, 5, 0, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.records()).singleElement().satisfies(item -> {
            assertThat(item.skuId()).isEqualTo(1001L);
            assertThat(item.type()).isEqualTo(5);
        });
    }

    private Inventory inventory(int total, int locked, int available) {
        Inventory inventory = new Inventory();
        inventory.setSkuId(1001L);
        inventory.setTotal(total);
        inventory.setLocked(locked);
        inventory.setAvailable(available);
        return inventory;
    }
}
