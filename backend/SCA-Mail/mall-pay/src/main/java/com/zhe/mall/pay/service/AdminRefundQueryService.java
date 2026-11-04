package com.zhe.mall.pay.service;

import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.pay.entity.Refund;
import com.zhe.mall.pay.enums.RefundStatus;
import com.zhe.mall.pay.mapper.RefundMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 后台退款查询（客服 / 运营视角）。
 *
 * <p>与 {@link RefundService} 的分工：本类只读，不改退款状态；
 * 退款申请、渠道退款与幂等仍在 {@link RefundService}，避免"查询"和"资金动作"混在一个类里。
 *
 * <p>角色校验不在服务层：mall-pay 的 {@code AdminRefundController} 标注 {@code @RequireStaff}，
 * BFF 侧再校验一次。
 */
@Service
public class AdminRefundQueryService {

    private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final Pattern DATE_TIME =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

    private final RefundMapper refundMapper;

    public AdminRefundQueryService(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    public PageResult<AdminRefundRow> page(AdminRefundPageQuery query) {
        AdminRefundPageQuery effective = normalize(query);
        long total = refundMapper.countAdminPage(effective);
        if (total == 0) {
            return PageResult.empty(effective.safePageNo(), effective.safePageSize());
        }
        List<AdminRefundRow> rows = refundMapper.selectAdminPage(
                        effective, effective.offset(), effective.safePageSize())
                .stream()
                .map(this::toRow)
                .toList();
        return PageResult.of(total, effective.safePageNo(), effective.safePageSize(), rows);
    }

    public AdminRefundDetail detail(String refundNo) {
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "refundNo 不能为空");
        }
        Refund refund = refundMapper.selectByRefundNo(refundNo.trim());
        if (refund == null) {
            throw new BusinessException(ResultCode.REFUND_NOT_FOUND, "退款单不存在: " + refundNo);
        }
        return new AdminRefundDetail(toRow(refund));
    }

    /** 待处理退款单数（看板概览）：待审核(0) + 退款中(10)。 */
    public long pendingCount() {
        return refundMapper.countPending();
    }

    private AdminRefundPageQuery normalize(AdminRefundPageQuery query) {
        if (query == null) {
            return new AdminRefundPageQuery(null, null, null, null, null, null, 1, 10);
        }
        if (query.status() != null) {
            // 提前校验：把 999 直接丢给 SQL 只会"查不到"，运营会误以为没有数据
            RefundStatus.of(query.status());
        }
        return new AdminRefundPageQuery(
                blankToNull(query.refundNo()),
                blankToNull(query.orderNo()),
                query.userId(),
                query.status(),
                normalizeTime(query.createdAtStart(), false),
                normalizeTime(query.createdAtEnd(), true),
                query.pageNo(),
                query.pageSize());
    }

    private String normalizeTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (DATE_ONLY.matcher(trimmed).matches()) {
                LocalDate date = LocalDate.parse(trimmed);
                LocalDateTime parsed = endOfDay
                        ? date.atTime(LocalTime.of(23, 59, 59, 999_000_000))
                        : date.atStartOfDay();
                return parsed.toString();
            }
            if (DATE_TIME.matcher(trimmed).matches()) {
                return LocalDateTime.parse(trimmed.replace(' ', 'T')).toString();
            }
        } catch (DateTimeParseException e) {
            throw invalidTime(trimmed);
        }
        throw invalidTime(trimmed);
    }

    private BusinessException invalidTime(String value) {
        return new BusinessException(ResultCode.PARAM_INVALID,
                "时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss: " + value);
    }

    private AdminRefundRow toRow(Refund refund) {
        return new AdminRefundRow(refund.getRefundNo(), refund.getPaymentNo(), refund.getOrderNo(),
                refund.getUserId(), refund.getAmount(), refund.getStatus(),
                RefundStatus.of(refund.getStatus()).label(), refund.getReason(),
                refund.getOperator(), refund.getOperatorId(),
                refund.getAuditedBy(), refund.getAuditedById(), refund.getAuditedAt(),
                refund.getChannelRefundNo(),
                refund.getAppliedAt(), refund.getFinishedAt(), refund.getFailReason(),
                refund.getCreatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
