package com.zhe.mall.pay.mapper;

import com.zhe.mall.pay.entity.PayReconcileDiff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PayReconcileDiffMapper {

    /** 落差异；{@code uk_diff} 保证重复跑不重复落（28.8）。 */
    int insert(PayReconcileDiff diff);

    List<PayReconcileDiff> selectByBizDate(@Param("bizDate") LocalDate bizDate);
}
