<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { navCategories } from '@/data/homeContent'

const activeId = ref('')

function queryFor(category) {
  return { name: 'search', query: { q: category.keyword } }
}
</script>

<template>
  <aside class="category-nav" aria-label="商品分类">
    <div class="category-nav__head">
      <h2 class="category-nav__title">分类</h2>
      <RouterLink class="category-nav__all" :to="{ name: 'search', query: { q: '' } }">
        全部
        <AppIcon name="chevron-right" :size="12" />
      </RouterLink>
    </div>

    <p class="category-nav__promo">
      <AppIcon name="ticket" :size="13" />
      券叠加低至 7.2 折起
    </p>

    <ul class="category-nav__list">
      <li
        v-for="category in navCategories"
        :key="category.id"
        class="category-nav__item"
        @mouseenter="activeId = category.id"
        @mouseleave="activeId = ''"
      >
        <RouterLink
          class="category-nav__link"
          :class="{ 'is-active': activeId === category.id }"
          :to="queryFor(category)"
        >
          <AppIcon :name="category.icon" :size="16" class="category-nav__icon" />
          <span class="category-nav__label">{{ category.name }}</span>
          <AppIcon name="chevron-right" :size="12" class="category-nav__chevron" />
        </RouterLink>
      </li>
    </ul>

    <div class="category-nav__footer">
      <AppIcon name="gift" :size="14" />
      <span>成为会员，专享更多好礼</span>
    </div>
  </aside>
</template>

<style scoped>
.category-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 18px 14px 14px;
  border-radius: var(--mall-radius);
  background: #fff;
  box-shadow: var(--mall-shadow-sm);
}

.category-nav__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-inline: 6px;
}

.category-nav__title {
  font-size: 17px;
  font-weight: 700;
}

.category-nav__all {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  color: var(--mall-ink-3);
  font-size: 12px;
  transition: color 0.15s var(--mall-ease);
}

.category-nav__all:hover {
  color: var(--mall-brand);
}

.category-nav__promo {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 6px 8px;
  padding: 7px 10px;
  border-radius: 8px;
  background: linear-gradient(90deg, #fff3ec, #fff8f4);
  color: var(--mall-brand);
  font-size: 12px;
  font-weight: 500;
}

.category-nav__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
}

.category-nav__item {
  min-height: 0;
}

.category-nav__link {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 8px;
  border-radius: 8px;
  color: var(--mall-ink-2);
  font-size: 13px;
  transition:
    background-color 0.15s var(--mall-ease),
    color 0.15s var(--mall-ease);
}

.category-nav__link:hover,
.category-nav__link.is-active {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.category-nav__icon {
  color: var(--mall-ink-4);
}

.category-nav__link:hover .category-nav__icon,
.category-nav__link.is-active .category-nav__icon {
  color: var(--mall-brand);
}

.category-nav__label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-nav__chevron {
  color: var(--mall-ink-4);
  opacity: 0;
  transition: opacity 0.15s var(--mall-ease);
}

.category-nav__link:hover .category-nav__chevron {
  opacity: 1;
}

.category-nav__footer {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 9px 10px;
  border-top: 1px dashed var(--mall-line);
  color: var(--mall-ink-3);
  font-size: 11px;
}

@media (max-width: 1080px) {
  .category-nav {
    padding: 14px 10px 10px;
  }

  .category-nav__link {
    padding: 6px 6px;
    font-size: 12px;
  }
}
</style>
