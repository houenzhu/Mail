<script setup>
import ComingSoon from '@/components/common/ComingSoon.vue'
</script>

<template>
  <ComingSoon
    title="库存流水"
    desc="按 SKU / 订单号 / 类型追溯每一次库存变动（预占、扣减、回滚、调整）。"
    :apis="['GET /api/admin/inventory/record/page（计划文档 G-07）']"
    milestone="M4"
  />
</template>
