<script setup>
/**
 * 商品状态标签。字典来源：@/constants/dict（对齐需求文档 16.5）。
 */
import { computed } from 'vue'

import { PRODUCT_STATUS_DICT, dictLabel, dictTag } from '@/constants/dict'

const props = defineProps({
  status: { type: Number, default: 0 },
  deleted: { type: Number, default: 0 },
})

const label = computed(() => (props.deleted === 1 ? '已删除' : dictLabel(PRODUCT_STATUS_DICT, props.status)))
const type = computed(() => (props.deleted === 1 ? 'info' : dictTag(PRODUCT_STATUS_DICT, props.status)))
</script>

<template>
  <el-tag :type="type" size="small" effect="light">{{ label }}</el-tag>
</template>
