<script setup>
/**
 * 待建模块占位页。
 *
 * 刻意不放假数据/假图表：把「缺哪个后端接口、为什么没有」直接写在页面上，
 * 避免以后误以为功能已经可用（对应计划文档 R-01 的 mock 先行策略之外的部分）。
 */
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  /** 依赖的后端接口清单 */
  apis: { type: Array, default: () => [] },
  milestone: { type: String, default: '' },
})
</script>

<template>
  <div class="page">
    <div class="page-card">
      <h2 class="page-title">{{ title }}</h2>
      <p v-if="desc" class="text-muted text-xs desc">{{ desc }}</p>
    </div>

    <div class="page-card">
      <el-alert type="info" :closable="false" show-icon title="该模块尚未接入">
        <template #default>
          <div class="alert-body">
            <p v-if="milestone" class="text-xs">
              计划里程碑：<strong>{{ milestone }}</strong>
            </p>
            <div v-if="apis.length" class="text-xs">
              依赖后端接口：
              <ul class="api-list">
                <li v-for="api in apis" :key="api">{{ api }}</li>
              </ul>
            </div>
          </div>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped>
.desc {
  margin: 6px 0 0;
}

.alert-body p {
  margin: 0 0 6px;
}

.api-list {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 20px;
}
</style>
