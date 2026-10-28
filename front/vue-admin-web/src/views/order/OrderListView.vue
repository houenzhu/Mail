<script setup>
import ComingSoon from '@/components/common/ComingSoon.vue'
</script>

<template>
  <ComingSoon
    title="订单管理"
    desc="全量订单查询、状态筛选、发货、查看订单详情与状态流转时间线。"
    :apis="[
      'GET /api/admin/order/page（全量订单，计划文档 G-04）',
      'GET /api/admin/order/{orderNo}（运营视角详情）',
      'POST /api/admin/order/{orderNo}/ship（发货 20→30，G-05 / UC-ORDER-07）',
    ]"
    milestone="M4"
  />
</template>
