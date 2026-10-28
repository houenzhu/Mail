package com.zhe.mall.pay.service;

import com.zhe.mall.common.util.SnowflakeIdGenerator;
import com.zhe.mall.pay.channel.ChannelBillItem;
import com.zhe.mall.pay.channel.PayChannel;
import com.zhe.mall.pay.dto.ReconcileDiffVO;
import com.zhe.mall.pay.dto.ReconcileResultVO;
import com.zhe.mall.pay.entity.PayReconcileDiff;
import com.zhe.mall.pay.entity.Payment;
import com.zhe.mall.pay.enums.ChannelTradeStatus;
import com.zhe.mall.pay.enums.ReconcileDiffType;
import com.zhe.mall.pay.mapper.PayReconcileDiffMapper;
import com.zhe.mall.pay.mapper.PaymentMapper;
import com.zhe.mall.pay.support.PayChannelRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 支付对账（28.8 / 22.10「支付对账」）。
 *
 * <p>比对口径：渠道流水（{@code PayChannel.bill(bizDate)}）vs 本地 {@code payment}
 * （{@code status = 20 且 paid_at 属于该日}），维度为 {@code channel_trade_no + amount + status}。
 *
 * <p>四类差异全部落 {@code pay_reconcile_diff}：{@code uk_diff(biz_date, payment_no, diff_type)}
 * 保证"同一天重复跑不重复落"，因此本任务可以安全重试。
 */
@Service
public class PayReconcileService {

    private static final Logger log = LoggerFactory.getLogger(PayReconcileService.class);

    private static final int DIFF_STATUS_PENDING = 0;

    private final PaymentMapper paymentMapper;

    private final PayReconcileDiffMapper diffMapper;

    private final PayChannelRegistry channelRegistry;

    private final ObjectProvider<MeterRegistry> meterRegistry;

    public PayReconcileService(PaymentMapper paymentMapper,
                               PayReconcileDiffMapper diffMapper,
                               PayChannelRegistry channelRegistry,
                               ObjectProvider<MeterRegistry> meterRegistry) {
        this.paymentMapper = paymentMapper;
        this.diffMapper = diffMapper;
        this.channelRegistry = channelRegistry;
        this.meterRegistry = meterRegistry;
    }

    /** 执行对账；返回差异清单（同时落表）。 */
    public ReconcileResultVO reconcile(LocalDate bizDate) {
        PayChannel channel = channelRegistry.current();
        List<ChannelBillItem> channelItems = channel.bill(bizDate);
        List<Payment> localPayments = paymentMapper.selectPaidBetween(
                bizDate.atStartOfDay(), bizDate.plusDays(1).atStartOfDay());

        Map<String, Payment> localByPaymentNo = new HashMap<>();
        Map<String, Payment> localByTradeNo = new HashMap<>();
        for (Payment payment : localPayments) {
            localByPaymentNo.put(payment.getPaymentNo(), payment);
            if (payment.getChannelTradeNo() != null && !payment.getChannelTradeNo().isBlank()) {
                localByTradeNo.put(payment.getChannelTradeNo(), payment);
            }
        }

        List<ReconcileDiffVO> diffs = new ArrayList<>();
        Set<String> matchedLocal = new HashSet<>();
        for (ChannelBillItem item : channelItems) {
            Payment local = null;
            if (item.paymentNo() != null) {
                local = localByPaymentNo.get(item.paymentNo());
            }
            if (local == null && item.channelTradeNo() != null) {
                local = localByTradeNo.get(item.channelTradeNo());
            }
            if (local == null) {
                // 渠道侧未结算（PENDING）的流水不参与差异判定：钱还没动，本地没有对应支付单是正常的。
                // 只有"渠道已结算但库里查不到"才是真差异（可能回调丢失或串单）
                if (item.status() != ChannelTradeStatus.SUCCESS) {
                    log.debug("渠道未结算流水，跳过: channelTradeNo={}, status={}",
                            item.channelTradeNo(), item.status());
                    continue;
                }
                diffs.add(new ReconcileDiffVO(item.paymentNo(), item.channelTradeNo(),
                        ReconcileDiffType.CHANNEL_ONLY.name(),
                        "渠道有、库中无: 渠道金额=" + item.amount() + ", 渠道状态=" + item.status()));
                continue;
            }
            matchedLocal.add(local.getPaymentNo());
            if (local.getAmount().compareTo(item.amount()) != 0) {
                diffs.add(new ReconcileDiffVO(local.getPaymentNo(), item.channelTradeNo(),
                        ReconcileDiffType.AMOUNT_MISMATCH.name(),
                        "金额不一致: 本地=" + local.getAmount() + ", 渠道=" + item.amount()));
            } else if (item.status() != ChannelTradeStatus.SUCCESS) {
                diffs.add(new ReconcileDiffVO(local.getPaymentNo(), item.channelTradeNo(),
                        ReconcileDiffType.STATUS_MISMATCH.name(),
                        "状态不一致: 本地=SUCCESS, 渠道=" + item.status()));
            }
        }
        for (Payment payment : localPayments) {
            if (!matchedLocal.contains(payment.getPaymentNo())) {
                diffs.add(new ReconcileDiffVO(payment.getPaymentNo(), payment.getChannelTradeNo(),
                        ReconcileDiffType.LOCAL_ONLY.name(),
                        "库中有、渠道无: 本地金额=" + payment.getAmount() + ", 本地状态="
                                + payment.getStatus() + "（虚假支付，需人工介入）"));
            }
        }

        int persisted = persist(bizDate, diffs);
        if (diffs.isEmpty()) {
            log.info("支付对账完成，无差异: bizDate={}, channelCount={}, localCount={}",
                    bizDate, channelItems.size(), localPayments.size());
        } else {
            log.error("支付对账发现差异: bizDate={}, diffCount={}, 新增落表={}, 明细={}",
                    bizDate, diffs.size(), persisted, diffs);
            recordMetric(diffs.size());
        }
        return new ReconcileResultVO(bizDate.toString(), channelItems.size(), localPayments.size(),
                diffs.size(), diffs);
    }

    /** 差异落表；{@code uk_diff} 命中说明重复跑，跳过即可（不是错误）。 */
    private int persist(LocalDate bizDate, List<ReconcileDiffVO> diffs) {
        int inserted = 0;
        for (ReconcileDiffVO vo : diffs) {
            PayReconcileDiff row = new PayReconcileDiff();
            row.setId(SnowflakeIdGenerator.nextId());
            row.setBizDate(bizDate);
            row.setPaymentNo(vo.paymentNo());
            row.setChannelTradeNo(vo.channelTradeNo());
            row.setDiffType(vo.diffType());
            row.setDetail(truncate(vo.detail()));
            row.setStatus(DIFF_STATUS_PENDING);
            try {
                inserted += diffMapper.insert(row);
            } catch (DuplicateKeyException e) {
                log.debug("对账差异已存在（重复跑），跳过: bizDate={}, paymentNo={}, type={}",
                        bizDate, vo.paymentNo(), vo.diffType());
            }
        }
        return inserted;
    }

    private void recordMetric(int diffCount) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry != null) {
            registry.counter("mall_pay_reconcile_diff_total").increment(diffCount);
        }
    }

    private String truncate(String detail) {
        if (detail == null || detail.length() <= 512) {
            return detail;
        }
        return detail.substring(0, 511) + "…";
    }
}
