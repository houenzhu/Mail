<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import AuthPageLayout from '@/components/auth/AuthPageLayout.vue'
import AuthCard from '@/components/auth/AuthCard.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import { useAuthForm } from '@/composables/useAuthForm'
import { useUserStore } from '@/stores/user'
import { ApiError, CODE } from '@/api/client'
import {
  clearRememberedUsername,
  getRememberedUsername,
  setRememberedUsername,
} from '@/utils/authPreferences'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const {
  form,
  errors,
  submitting,
  passwordVisible,
  touch,
  validateAll,
  resetFormError,
} = useAuthForm('login')

const usernameRef = ref(null)
const passwordRef = ref(null)

const redirectTarget = computed(() => {
  const value = route.query.redirect
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return { name: 'home' }
  }
  return value
})

const lockedHint = computed(() => {
  if (errors.form.includes('锁定')) return '连续输错 5 次会锁定 15 分钟，可稍后再试或联系客服。'
  return ''
})

function errorMessageOf(error) {
  if (!(error instanceof ApiError)) return '登录失败，请稍后重试'
  switch (error.code) {
    case CODE.PASSWORD_ERROR:
      return '账号或密码错误，请检查后重试'
    case CODE.ACCOUNT_LOCKED:
      return '账号已被锁定，请 15 分钟后再试'
    case CODE.ACCOUNT_DISABLED:
      return '账号已注销或停用，请联系客服'
    case CODE.RATE_LIMITED:
      return '尝试过于频繁，请稍后再试'
    case 'NETWORK':
      return '无法连接服务，请确认后端网关已启动'
    case 'TIMEOUT':
      return '请求超时，请稍后重试'
    default:
      return error.message || '登录失败，请稍后重试'
  }
}

async function submit() {
  resetFormError()
  const firstError = validateAll()
  if (firstError) {
    await nextTick()
    const refs = { username: usernameRef, password: passwordRef }
    refs[firstError]?.value?.focus()
    return
  }

  submitting.value = true
  try {
    const username = form.username.trim()
    await userStore.login({ username, password: form.password })
    if (form.remember) setRememberedUsername(username)
    else clearRememberedUsername()
    userStore.clearUnauthorizedMessage()
    await router.replace(redirectTarget.value)
  } catch (error) {
    errors.form = errorMessageOf(error)
    await nextTick()
    passwordRef.value?.focus()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  const remembered = getRememberedUsername()
  if (remembered) {
    form.username = remembered
    form.remember = true
  }

  if (userStore.isLoggedIn) router.replace(redirectTarget.value)
  if (route.query.reason === 'expired') {
    errors.form = '登录已过期，请重新登录'
  }
  if (route.query.registered === '1') {
    form.username = String(route.query.username || remembered)
  }

  if (form.username) passwordRef.value?.focus()
  else usernameRef.value?.focus()
})
</script>

<template>
  <AuthPageLayout aside-label="好物集简介">
    <template #header-aside>
      <RouterLink :to="{ name: 'home' }">返回商城</RouterLink>
      <span aria-hidden="true">·</span>
      <RouterLink :to="{ name: 'feature', params: { feature: 'help' } }">帮助中心</RouterLink>
    </template>

    <template #intro>
      <p class="auth-intro__eyebrow">
        <AppIcon name="sparkle" :size="14" />
        GOODMALL
      </p>
      <h1>把喜欢的东西，<br />慢慢带回家。</h1>
      <p class="auth-intro__desc">
        登录后可以同步收藏、查看订单，也能在不同设备间继续上次的挑选。
      </p>
      <ul class="auth-intro__points">
        <li>
          <AppIcon name="shield-check" :size="16" />
          登录状态加密保存，支持自动续期
        </li>
        <li>
          <AppIcon name="heart" :size="16" />
          收藏与浏览记录跨设备同步
        </li>
        <li>
          <AppIcon name="ticket" :size="16" />
          订单进度与售后入口集中管理
        </li>
      </ul>
      <p class="auth-intro__tip">
        演示账号：<strong>buyer01</strong> / <strong>Buyer@123456</strong>
      </p>
    </template>

    <AuthCard title="欢迎回来" subtitle="登录后继续你的挑选" title-id="login-title">
      <form class="auth-form" novalidate @submit.prevent="submit">
        <div class="auth-field" :class="{ 'has-error': errors.username }">
          <label for="login-username">用户名</label>
          <div class="auth-field__control">
            <AppIcon name="user" :size="17" />
            <input
              id="login-username"
              ref="usernameRef"
              v-model="form.username"
              type="text"
              name="username"
              autocomplete="username"
              placeholder="请输入用户名"
              :aria-invalid="Boolean(errors.username)"
              :aria-describedby="errors.username ? 'login-username-error' : undefined"
              @blur="touch('username')"
            />
          </div>
          <p v-if="errors.username" id="login-username-error" class="auth-field__error">
            {{ errors.username }}
          </p>
        </div>

        <div class="auth-field" :class="{ 'has-error': errors.password }">
          <label for="login-password">密码</label>
          <div class="auth-field__control">
            <AppIcon name="shield" :size="17" />
            <input
              id="login-password"
              ref="passwordRef"
              v-model="form.password"
              :type="passwordVisible ? 'text' : 'password'"
              name="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              :aria-invalid="Boolean(errors.password)"
              :aria-describedby="errors.password ? 'login-password-error' : undefined"
              @blur="touch('password')"
            />
            <button
              class="auth-field__toggle"
              type="button"
              :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
              @click="passwordVisible = !passwordVisible"
            >
              <AppIcon :name="passwordVisible ? 'eye-off' : 'eye'" :size="17" />
            </button>
          </div>
          <p v-if="errors.password" id="login-password-error" class="auth-field__error">
            {{ errors.password }}
          </p>
        </div>

        <div class="auth-row">
          <label class="auth-check">
            <input v-model="form.remember" type="checkbox" />
            <span>记住用户名</span>
          </label>
          <RouterLink class="auth-link" :to="{ name: 'feature', params: { feature: 'help' } }">
            忘记密码？
          </RouterLink>
        </div>

        <p v-if="errors.form" class="auth-alert" role="alert">
          <AppIcon name="close" :size="14" />
          {{ errors.form }}
        </p>
        <p v-if="lockedHint" class="auth-hint">{{ lockedHint }}</p>

        <BaseButton class="auth-submit" type="submit" size="lg" block :loading="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </BaseButton>

        <p class="auth-switch">
          还没有账号？
          <RouterLink :to="{ name: 'register', query: route.query }">免费注册</RouterLink>
        </p>
      </form>
    </AuthCard>

    <template #footer>
      <SiteFooter />
    </template>
  </AuthPageLayout>
</template>

<style scoped>
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 24px;
}

.auth-field label {
  display: block;
  margin-bottom: 8px;
  color: var(--mall-ink-2);
  font-size: 12px;
  font-weight: 600;
}

.auth-field__control {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 14px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 12px;
  background: #fff;
  color: var(--mall-ink-3);
  transition:
    border-color 0.18s var(--mall-ease),
    box-shadow 0.18s var(--mall-ease);
}

.auth-field__control:focus-within {
  border-color: var(--mall-brand);
  box-shadow: 0 0 0 4px rgba(255, 80, 0, 0.1);
}

.auth-field.has-error .auth-field__control {
  border-color: #d93a3a;
}

.auth-field__control input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  font-size: 14px;
  outline: none;
}

.auth-field__control input::placeholder {
  color: var(--mall-ink-4);
}

.auth-field__toggle {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: var(--mall-ink-3);
}

.auth-field__toggle:hover {
  background: var(--mall-surface-2);
  color: var(--mall-ink);
}

.auth-field__error {
  margin-top: 6px;
  color: #c53030;
  font-size: 12px;
}

.auth-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: -4px;
}

.auth-check {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--mall-ink-2);
  font-size: 12px;
  cursor: pointer;
}

.auth-check input {
  width: 15px;
  height: 15px;
  accent-color: var(--mall-brand);
}

.auth-link {
  color: var(--mall-ink-3);
  font-size: 12px;
}

.auth-link:hover {
  color: var(--mall-brand);
}

.auth-alert {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff1f0;
  color: #c53030;
  font-size: 12px;
  line-height: 1.6;
}

.auth-alert :deep(svg) {
  flex-shrink: 0;
  margin-top: 2px;
}

.auth-hint {
  color: var(--mall-ink-3);
  font-size: 11px;
  line-height: 1.6;
}

.auth-submit {
  margin-top: 2px;
}

.auth-switch {
  color: var(--mall-ink-3);
  font-size: 12px;
  text-align: center;
}

.auth-switch a {
  margin-left: 4px;
  color: var(--mall-brand);
  font-weight: 600;
}
</style>