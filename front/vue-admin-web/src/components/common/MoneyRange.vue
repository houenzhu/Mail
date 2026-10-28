<script setup>
import { formatMoneyRange } from '@/utils/format'

const props = defineProps({
  min: { type: [Number, String], default: null },
  max: { type: [Number, String], default: null },
})
</script>

<template>
  <span class="money">{{ formatMoneyRange(props.min, props.max) }}</span>
</template>
