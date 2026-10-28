<script setup>
import ComingSoon from '@/components/common/ComingSoon.vue'
</script>

<template>
  <ComingSoon
    title="退款处理"
    desc="退款单列表、状态筛选与详情。当前仅支持「已支付且未发货」的全额退款（BR-13）。"
    :apis="[
      'GET /api/pay/refund/{refundNo}（单条查询，已有）',
      'GET /api/admin/refund/page（退款单分页，待建）',
    ]"
    milestone="M4"
  />
</template>
