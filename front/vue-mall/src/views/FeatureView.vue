<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'

const route = useRoute()

const featureMap = {
  login: {
    title: '登录功能正在准备中',
    desc: '账号体系将在第二阶段上线，届时支持手机号与邮箱登录、短信验证码和记住登录状态。',
    icon: 'user',
    stage: '第二阶段',
    points: ['手机号 / 邮箱登录', '验证码与密码双通道', '登录态安全持久化'],
  },
  register: {
    title: '注册功能正在准备中',
    desc: '注册将和登录一起在第二阶段交付，包含协议确认、密码强度提示与基础资料完善。',
    icon: 'user',
    stage: '第二阶段',
    points: ['手机号快速注册', '协议与隐私确认', '注册后引导完善资料'],
  },
  cart: {
    title: '购物车正在准备中',
    desc: '购物车会在商品详情页之后接入，支持规格选择、库存校验与跨端同步。',
    icon: 'cart',
    stage: '第三阶段',
    points: ['SKU 规格选择', '库存实时校验', '购物车持久化'],
  },
  orders: {
    title: '订单中心正在准备中',
    desc: '登录与下单流程完成后，这里会展示订单列表、物流进度与售后入口。',
    icon: 'ticket',
    stage: '第四阶段',
    points: ['订单状态追踪', '物流信息', '退款与售后'],
  },
  favorites: {
    title: '收藏夹正在准备中',
    desc: '收藏功能将和账号体系一起上线，支持商品收藏、降价提醒与分组管理。',
    icon: 'heart',
    stage: '第二阶段',
    points: ['一键收藏商品', '降价提醒', '自定义收藏分组'],
  },
  'product-detail': {
    title: '商品详情页正在准备中',
    desc: '首页商品卡片已经接入真实数据。下一阶段会补上详情页的规格选择、库存与加购能力。',
    icon: 'eye',
    stage: '第三阶段',
    points: ['主图与详情展示', 'SKU 规格联动', '加入购物车'],
  },
  help: {
    title: '帮助中心正在准备中',
    desc: '常见问题、退换货说明和客服入口会在后续阶段与客服模块一起上线。',
    icon: 'headset',
    stage: '第五阶段',
    points: ['常见问题检索', '在线客服', '售后工单'],
  },
  member: {
    title: '会员中心正在准备中',
    desc: '会员等级、积分与专属权益将结合登录体系上线，为高频用户提供更多价值。',
    icon: 'gift',
    stage: '第二阶段',
    points: ['积分累计与抵现', '会员专享价', '生日礼遇'],
  },
  seller: {
    title: '商家入驻正在准备中',
    desc: '商家端与管理后台分属不同产品线，本阶段先聚焦买家购物体验。',
    icon: 'trend',
    stage: '后续规划',
    points: ['入驻申请', '店铺管理', '经营数据'],
  },
  merchant: {
    title: '商家中心正在准备中',
    desc: '商家中心会复用现有管理后台能力，后续以独立入口提供服务。',
    icon: 'grid',
    stage: '后续规划',
    points: ['商品与库存管理', '订单发货', '售后处理'],
  },
  accessibility: {
    title: '无障碍服务正在准备中',
    desc: '我们会持续补充键盘导航、屏幕阅读器语义与高对比度支持，让每个人都能顺畅购物。',
    icon: 'shield-check',
    stage: '持续优化',
    points: ['键盘完整可操作', '语义化结构', '减少动效支持'],
  },
}

const fallback = {
  title: '功能正在准备中',
  desc: '这个入口会在后续阶段开放，先去首页逛逛精选好物吧。',
  icon: 'sparkle',
  stage: '后续阶段',
  points: ['首页已完整可用', '商品搜索已接入真实接口', '更多能力持续交付'],
}

const feature = computed(() => featureMap[route.params.feature] || fallback)
</script>

<template>
  <div class="feature-page">
    <SiteHeader />

    <main class="mall-container feature-main">
      <div class="feature-card">
        <span class="feature-card__icon">
          <AppIcon :name="feature.icon" :size="28" />
        </span>
        <span class="feature-card__stage">{{ feature.stage }}</span>
        <h1>{{ feature.title }}</h1>
        <p class="feature-card__desc">{{ feature.desc }}</p>

        <ul class="feature-card__points">
          <li v-for="point in feature.points" :key="point">
            <AppIcon name="check" :size="14" />
            {{ point }}
          </li>
        </ul>

        <div class="feature-card__actions">
          <BaseButton :to="{ name: 'home' }">回到首页</BaseButton>
          <BaseButton variant="ghost" :to="{ name: 'search', query: { q: '' } }">
            浏览全部商品
          </BaseButton>
        </div>
      </div>

      <aside class="feature-note">
        <h2>为什么先做首页？</h2>
        <p>
          首页是 C 端流量入口，也是组件复用率最高的页面。先把布局、卡片、搜索和接口适配做稳，
          后续详情、购物车与登录页可以直接复用，不必返工。
        </p>
        <RouterLink class="feature-note__link" :to="{ name: 'home' }">
          查看首页实现
          <AppIcon name="arrow-right" :size="14" />
        </RouterLink>
      </aside>
    </main>

    <SiteFooter />
  </div>
</template>

<style scoped>
.feature-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.feature-main {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  padding-top: 28px;
}

.feature-card {
  padding: 40px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  box-shadow: var(--mall-shadow-sm);
}

.feature-card__icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.feature-card__stage {
  display: inline-flex;
  margin-top: 18px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--mall-surface-2);
  color: var(--mall-ink-3);
  font-size: 11px;
  font-weight: 600;
}

.feature-card h1 {
  margin-top: 12px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.feature-card__desc {
  max-width: 520px;
  margin-top: 10px;
  color: var(--mall-ink-2);
  font-size: 14px;
  line-height: 1.8;
}

.feature-card__points {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 22px;
}

.feature-card__points li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mall-ink-2);
  font-size: 13px;
}

.feature-card__points :deep(svg) {
  color: var(--mall-success);
}

.feature-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.feature-note {
  padding: 26px;
  border-radius: var(--mall-radius-lg);
  background: linear-gradient(150deg, #2a2a2a, #151515);
  color: #fff;
}

.feature-note h2 {
  font-size: 17px;
  font-weight: 700;
}

.feature-note p {
  margin-top: 12px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 13px;
  line-height: 1.85;
}

.feature-note__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 20px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.feature-note__link:hover {
  color: #ffb590;
}

@media (max-width: 820px) {
  .feature-main {
    grid-template-columns: 1fr;
    padding-top: 18px;
  }

  .feature-card {
    padding: 26px 22px;
  }

  .feature-card h1 {
    font-size: 22px;
  }
}
</style>
