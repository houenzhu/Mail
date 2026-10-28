<script setup>
/**
 * 数据看板。依赖 `GET /api/admin/dashboard`（计划文档 G-10，尚未实现）。
 * 计划在 M5 引入 ECharts；当前先给出明确占位，不画假图表。
 */
</script>

<template>
  <div class="page">
    <div class="page-card">
      <h2 class="page-title">数据看板</h2>
      <p class="text-muted text-xs">GMV / 订单量 / 售罄率趋势</p>
    </div>

    <div class="page-card">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="指标接口尚未实现"
        description="需要后端提供 GET /api/admin/dashboard（UC-ADMIN-03）。接口就绪后接入 ECharts 展示趋势图。在此之前不展示任何估算数字，避免误导运营决策。"
      />
    </div>
  </div>
</template>
