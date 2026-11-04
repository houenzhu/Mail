<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'

const props = defineProps({
  variant: { type: String, default: 'default' },
  initialKeyword: { type: String, default: '' },
})

const emit = defineEmits(['submit'])

const router = useRouter()
const route = useRoute()
const keyword = ref(props.initialKeyword || '')
const focused = ref(false)

watch(
  () => route.query.q,
  (value) => {
    if (typeof value === 'string' && value !== keyword.value) keyword.value = value
  },
)

watch(
  () => props.initialKeyword,
  (value) => {
    if (value !== undefined && value !== keyword.value) keyword.value = value
  },
)

const hotKeywords = ['降噪耳机', '纯棉T恤', '休闲单椅', '保湿面霜', '机械键盘']

function submit(term) {
  const value = String(term ?? keyword.value).trim()
  if (!value) return
  keyword.value = value
  emit('submit', value)
  router.push({ name: 'search', query: { q: value } })
}
</script>

<template>
  <form
    class="search-bar"
    :class="[`search-bar--${variant}`, { 'is-focused': focused }]"
    role="search"
    @submit.prevent="submit()"
  >
    <label class="visually-hidden" for="mall-search-input">搜索商品</label>
    <span class="search-bar__icon" aria-hidden="true">
      <AppIcon name="search" :size="19" />
    </span>
    <input
      id="mall-search-input"
      v-model="keyword"
      class="search-bar__input"
      type="search"
      name="q"
      placeholder="搜索宝贝、品牌或店铺"
      autocomplete="off"
      enterkeyhint="search"
      @focus="focused = true"
      @blur="focused = false"
    />
    <button class="search-bar__button" type="submit">搜索</button>
  </form>

  <div v-if="variant === 'hero'" class="search-bar__hot">
    <span class="search-bar__hot-label">热门搜索</span>
    <button
      v-for="term in hotKeywords"
      :key="term"
      class="search-bar__hot-item"
      type="button"
      @click="submit(term)"
    >
      {{ term }}
    </button>
  </div>
</template>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  height: 46px;
  padding: 4px 4px 4px 18px;
  border: 2px solid var(--mall-brand);
  border-radius: 999px;
  background: #fff;
  transition: box-shadow 0.2s var(--mall-ease);
}

.search-bar.is-focused {
  box-shadow: 0 0 0 4px rgba(255, 80, 0, 0.12);
}

.search-bar__icon {
  color: var(--mall-ink-3);
}

.search-bar__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 12px;
  border: 0;
  background: transparent;
  font-size: 14px;
  outline: none;
}

.search-bar__input::placeholder {
  color: var(--mall-ink-4);
}

.search-bar__input::-webkit-search-cancel-button {
  display: none;
}

.search-bar__button {
  height: 100%;
  padding: 0 28px;
  border-radius: 999px;
  background: var(--mall-brand);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: background-color 0.2s var(--mall-ease);
}

.search-bar__button:hover {
  background: var(--mall-brand-deep);
}

.search-bar--hero {
  height: 50px;
  border-color: #fff;
  box-shadow: 0 10px 30px rgba(31, 31, 31, 0.16);
}

.search-bar--hero.is-focused {
  box-shadow:
    0 10px 30px rgba(31, 31, 31, 0.16),
    0 0 0 4px rgba(255, 80, 0, 0.18);
}

.search-bar__hot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 12px;
  color: rgba(31, 31, 31, 0.62);
  font-size: 12px;
}

.search-bar__hot-label {
  color: rgba(31, 31, 31, 0.42);
}

.search-bar__hot-item {
  font-size: 12px;
  color: rgba(31, 31, 31, 0.66);
  transition: color 0.15s var(--mall-ease);
}

.search-bar__hot-item:hover {
  color: var(--mall-brand);
  text-decoration: underline;
}

@media (max-width: 768px) {
  .search-bar {
    height: 42px;
    padding-left: 14px;
  }

  .search-bar__button {
    padding: 0 18px;
    font-size: 14px;
  }

  .search-bar--hero {
    height: 44px;
  }

  .search-bar__hot {
    display: none;
  }
}
</style>
