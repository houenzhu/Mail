package com.zhe.mall.pay.mapper;

import com.zhe.mall.pay.entity.PaymentCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentCallbackLogMapper {

    /**
     * 落流水（28.6 纪律 1：先落流水再做业务）。
     *
     * <p>带 {@code channel_trade_no} 的插入会占用 {@code uk_event}，重复事件抛
     * {@code DuplicateKeyException}——这正是防重放的判定依据。
     */
    int insert(PaymentCallbackLog callbackLog);

    List<PaymentCallbackLog> selectByPaymentNo(@Param("paymentNo") String paymentNo);

    /**
     * 回填处理结果（第 5 / 6 步失败时把已落库的流水从"成功"改成真实结果）。
     *
     * <p>流水必须先插入再改状态（纪律 1），因此"金额不一致""状态非法"这两类失败是
     * {@code UPDATE} 而不是重新 {@code INSERT}——重插会撞 {@code uk_event}。
     */
    int updateProcessResult(@Param("id") Long id, @Param("processResult") int processResult);
}
