package com.zhe.mall.pay.dto;

import java.util.List;

/** 对账结果（28.8）：渠道条数 / 本地条数 / 差异清单。 */
public record ReconcileResultVO(String bizDate,
                                int channelCount,
                                int localCount,
                                int diffCount,
                                List<ReconcileDiffVO> diffs) {
}
