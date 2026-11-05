<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import FloatingToolbar from '@/components/layout/FloatingToolbar.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loggingOut = ref(false)
const notice = ref('')

const profile = computed(() => userStore.profile)
const initial = computed(() => {
  const name = userStore.displayName || ''
  return name.trim().slice(0, 1).toUpperCase() || '好'
})

const lastLoginText = computed(() => {
  const value = profile.value?.lastLoginAt
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
})

const quickLinks = [
  { label: '我的订单', desc: '查看订单与物流', icon: 'ticket', feature: 'orders', to: { name: 'order-list' } },
  { label: '我的收藏', desc: '收好喜欢的宝贝', icon: 'heart', feature: 'favorites' },
  { label: '购物车', desc: '待结算的商品', icon: 'cart', feature: 'cart', to: { name: 'cart' } },
  { label: '会员权益', desc: '积分与专属优惠', icon: 'gift', feature: 'member' },
]

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  notice.value = ''
  try {
    await userStore.logout()
    await router.replace({ name: 'home' })
  } catch {
    notice.value = '退出登录失败，请稍后重试'
  } finally {
    loggingOut.value = false
  }
}

onMounted(() => {
  userStore.hydrateProfile()
})
</script>

<template>
  <div class="account-page">
    <SiteHeader />

    <main class="mall-container account-main">
      <nav class="account-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'home' }">首页</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>账户中心</span>
      </nav>

      <section class="account-hero">
        <div class="account-hero__identity">
          <span class="account-hero__avatar" aria-hidden="true">{{ initial }}</span>
          <div class="account-hero__text">
            <p class="account-hero__greeting">你好，{{ userStore.displayName }}</p>
            <p class="account-hero__meta">
              <span v-if="profile?.username">@{{ profile.username }}</span>
              <span v-if="profile?.phone">{{ profile.phone }}</span>
              <span v-if="lastLoginText">上次登录 {{ lastLoginText }}</span>
            </p>
          </div>
        </div>

        <div class="account-hero__actions">
          <BaseButton variant="ghost" :to="{ name: 'home' }">继续逛逛</BaseButton>
          <BaseButton
            variant="text"
            :loading="loggingOut"
            class="account-hero__logout"
            @click="logout"
          >
            退出登录
          </BaseButton>
        </div>
      </section>

      <p v-if="notice" class="account-notice" role="alert">{{ notice }}</p>

      <section class="account-grid" aria-label="账户快捷入口">
        <RouterLink
          v-for="link in quickLinks"
          :key="link.feature"
          class="account-card"
          :to="link.to || { name: 'feature', params: { feature: link.feature } }"
        >
          <span class="account-card__icon">
            <AppIcon :name="link.icon" :size="21" />
          </span>
          <div>
            <h2>{{ link.label }}</h2>
            <p>{{ link.desc }}</p>
          </div>
          <AppIcon name="arrow-right" :size="15" class="account-card__arrow" />
        </RouterLink>
      </section>

      <section class="account-panel">
        <div class="account-panel__head">
          <h2>账号资料</h2>
          <span>登录状态已安全保存</span>
        </div>

        <dl class="account-fields">
          <div>
            <dt>用户名</dt>
            <dd>{{ profile?.username || '—' }}</dd>
          </div>
          <div>
            <dt>昵称</dt>
            <dd>{{ profile?.nickname || '—' }}</dd>
          </div>
          <div>
            <dt>手机号</dt>
            <dd>{{ profile?.phone || '—' }}</dd>
          </div>
          <div>
            <dt>账号角色</dt>
            <dd>{{ (profile?.roles || []).join(' / ') || 'USER' }}</dd>
          </div>
        </dl>
      </section>
    </main>

    <SiteFooter />
    <FloatingToolbar />
  </div>
</template>

<style scoped>
.account-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.account-main {
  flex: 1;
  padding-top: 18px;
}

.account-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.account-breadcrumb a:hover {
  color: var(--mall-brand);
}

.account-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 14px;
  padding: 26px 28px;
  border-radius: var(--mall-radius-lg);
  background:
    radial-gradient(520px 200px at 100% 0%, rgba(255, 80, 0, 0.12), transparent 70%),
    #fff;
  box-shadow: var(--mall-shadow-sm);
}

.account-hero__identity {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.account-hero__avatar {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: linear-gradient(140deg, #ff8a4c, #ff5000);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
}

.account-hero__greeting {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.account-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 6px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.account-hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.account-hero__logout {
  color: var(--mall-ink-3);
}

.account-notice {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff7e6;
  color: #b7791f;
  font-size: 12px;
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.account-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px;
  border-radius: var(--mall-radius);
  background: #fff;
  transition:
    transform 0.24s var(--mall-ease),
    box-shadow 0.24s var(--mall-ease);
}

.account-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--mall-shadow);
}

.account-card__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
  flex-shrink: 0;
}

.account-card h2 {
  font-size: 14px;
  font-weight: 700;
}

.account-card p {
  margin-top: 3px;
  color: var(--mall-ink-3);
  font-size: 11px;
}

.account-card__arrow {
  margin-left: auto;
  color: var(--mall-ink-4);
}

.account-panel {
  margin-top: 18px;
  padding: 24px 26px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  box-shadow: var(--mall-shadow-sm);
}

.account-panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mall-line);
}

.account-panel__head h2 {
  font-size: 17px;
  font-weight: 700;
}

.account-panel__head span {
  color: var(--mall-ink-3);
  font-size: 11px;
}

.account-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 24px;
  margin-top: 18px;
}

.account-fields dt {
  color: var(--mall-ink-3);
  font-size: 11px;
}

.account-fields dd {
  margin-top: 4px;
  color: var(--mall-ink);
  font-size: 14px;
  font-weight: 500;
}

@media (max-width: 900px) {
  .account-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .account-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 20px;
  }

  .account-hero__greeting {
    font-size: 18px;
  }

  .account-hero__actions {
    width: 100%;
    justify-content: space-between;
  }

  .account-grid {
    gap: 10px;
  }

  .account-card {
    padding: 14px;
  }

  .account-card p {
    display: none;
  }

  .account-panel {
    padding: 18px;
  }

  .account-fields {
    grid-template-columns: 1fr;
    gap: 14px;
  }
}
</style>