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

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const {
  form,
  errors,
  submitting,
  passwordVisible,
  passwordStrength,
  passwordHints,
  touch,
  validateAll,
  resetFormError,
} = useAuthForm('register')

const usernameRef = ref(null)
const phoneRef = ref(null)
const passwordRef = ref(null)
const nicknameRef = ref(null)

const strengthLabel = computed(() => {
  if (!form.password) return ''
  return ['', '偏弱', '一般', '安全'][passwordStrength.value] || ''
})

const strengthTone = computed(() => {
  if (!form.password) return 'none'
  return ['', 'weak', 'medium', 'strong'][passwordStrength.value] || 'none'
})

function errorMessageOf(error) {
  if (!(error instanceof ApiError)) return '注册失败，请稍后重试'
  switch (error.code) {
    case CODE.PHONE_REGISTERED:
      return '该手机号已注册，可直接登录'
    case CODE.USERNAME_REGISTERED:
      return '该用户名已存在，换一个试试'
    case CODE.PARAM_ERROR:
    case CODE.PARAM_INVALID:
      return error.message || '请检查填写内容'
    case CODE.RATE_LIMITED:
      return '注册过于频繁，请稍后再试'
    case 'NETWORK':
      return '无法连接服务，请确认后端网关已启动'
    case 'TIMEOUT':
      return '请求超时，请稍后重试'
    default:
      return error.message || '注册失败，请稍后重试'
  }
}

async function submit() {
  resetFormError()
  const firstError = validateAll()
  if (firstError) {
    await nextTick()
    const refs = {
      username: usernameRef,
      password: passwordRef,
      phone: phoneRef,
      nickname: nicknameRef,
    }
    refs[firstError]?.value?.focus()
    return
  }

  submitting.value = true
  try {
    await userStore.register({
      username: form.username.trim(),
      password: form.password,
      phone: form.phone.trim(),
      nickname: form.nickname.trim(),
    })
    await router.replace({
      name: 'login',
      query: {
        registered: '1',
        username: form.username.trim(),
        ...(route.query.redirect ? { redirect: route.query.redirect } : {}),
      },
    })
  } catch (error) {
    errors.form = errorMessageOf(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (userStore.isLoggedIn) router.replace({ name: 'home' })
  usernameRef.value?.focus()
})
</script>

<template>
  <AuthPageLayout aside-label="注册说明">
    <template #header-aside>
      <span>已有账号？</span>
      <RouterLink class="auth-header__login" :to="{ name: 'login', query: route.query }">
        直接登录
      </RouterLink>
    </template>

    <template #intro>
      <p class="auth-intro__eyebrow">
        <AppIcon name="sparkle" :size="14" />
        JOIN GOODMALL
      </p>
      <h1>从一件喜欢的东西开始，<br />把生活过成自己的样子。</h1>
      <p class="auth-intro__desc">
        注册只需一分钟。我们会用手机号做账号安全校验，不会向第三方出售你的信息。
      </p>
      <ul class="auth-intro__points">
        <li>
          <AppIcon name="shield-check" :size="16" />
          密码使用 BCrypt 加密存储
        </li>
        <li>
          <AppIcon name="heart" :size="16" />
          收藏、订单与浏览记录集中管理
        </li>
        <li>
          <AppIcon name="gift" :size="16" />
          会员日与专属优惠优先触达
        </li>
      </ul>
    </template>

    <AuthCard title="创建账号" subtitle="填写基本信息，即可开始逛好物" title-id="register-title">
      <form class="auth-form" novalidate @submit.prevent="submit">
        <div class="auth-field" :class="{ 'has-error': errors.username }">
          <label for="register-username">用户名</label>
          <div class="auth-field__control">
            <AppIcon name="user" :size="17" />
            <input
              id="register-username"
              ref="usernameRef"
              v-model="form.username"
              type="text"
              name="username"
              autocomplete="username"
              placeholder="4~32 位字母、数字或下划线"
              :aria-invalid="Boolean(errors.username)"
              @blur="touch('username')"
            />
          </div>
          <p v-if="errors.username" class="auth-field__error">{{ errors.username }}</p>
        </div>

        <div class="auth-field" :class="{ 'has-error': errors.phone }">
          <label for="register-phone">手机号</label>
          <div class="auth-field__control">
            <AppIcon name="phone" :size="17" />
            <input
              id="register-phone"
              ref="phoneRef"
              v-model="form.phone"
              type="tel"
              name="phone"
              inputmode="numeric"
              autocomplete="tel"
              maxlength="11"
              placeholder="用于账号安全校验"
              :aria-invalid="Boolean(errors.phone)"
              @blur="touch('phone')"
            />
          </div>
          <p v-if="errors.phone" class="auth-field__error">{{ errors.phone }}</p>
        </div>

        <div class="auth-field" :class="{ 'has-error': errors.nickname }">
          <label for="register-nickname">昵称 <span class="auth-field__optional">选填</span></label>
          <div class="auth-field__control">
            <AppIcon name="heart" :size="17" />
            <input
              id="register-nickname"
              ref="nicknameRef"
              v-model="form.nickname"
              type="text"
              name="nickname"
              autocomplete="nickname"
              maxlength="32"
              placeholder="想让我们怎么称呼你"
              :aria-invalid="Boolean(errors.nickname)"
              @blur="touch('nickname')"
            />
          </div>
          <p v-if="errors.nickname" class="auth-field__error">{{ errors.nickname }}</p>
        </div>

        <div class="auth-field" :class="{ 'has-error': errors.password }">
          <label for="register-password">密码</label>
          <div class="auth-field__control">
            <AppIcon name="shield" :size="17" />
            <input
              id="register-password"
              ref="passwordRef"
              v-model="form.password"
              :type="passwordVisible ? 'text' : 'password'"
              name="password"
              autocomplete="new-password"
              placeholder="8~32 位，需含字母和数字"
              :aria-invalid="Boolean(errors.password)"
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
          <div v-if="form.password" class="password-meter" :class="`is-${strengthTone}`">
            <span class="password-meter__bar" />
            <span class="password-meter__bar" />
            <span class="password-meter__bar" />
            <span class="password-meter__label">{{ strengthLabel }}</span>
          </div>
          <ul class="password-hints">
            <li
              v-for="rule in passwordHints"
              :key="rule.id"
              :class="{ 'is-passed': rule.passed }"
            >
              <AppIcon :name="rule.passed ? 'check' : 'close'" :size="12" />
              {{ rule.label }}
            </li>
          </ul>
          <p v-if="errors.password" class="auth-field__error">{{ errors.password }}</p>
        </div>

        <div class="auth-field auth-field--agree" :class="{ 'has-error': errors.agree }">
          <label class="auth-check">
            <input v-model="form.agree" type="checkbox" />
            <span>
              我已阅读并同意
              <RouterLink :to="{ name: 'feature', params: { feature: 'help' } }">
                《用户协议》
              </RouterLink>
              与
              <RouterLink :to="{ name: 'feature', params: { feature: 'help' } }">
                《隐私政策》
              </RouterLink>
            </span>
          </label>
          <p v-if="errors.agree" class="auth-field__error">{{ errors.agree }}</p>
        </div>

        <p v-if="errors.form" class="auth-alert" role="alert">
          <AppIcon name="close" :size="14" />
          {{ errors.form }}
        </p>

        <BaseButton class="auth-submit" type="submit" size="lg" block :loading="submitting">
          {{ submitting ? '创建中…' : '创建账号' }}
        </BaseButton>

        <p class="auth-switch">
          已经有账号？
          <RouterLink :to="{ name: 'login', query: route.query }">去登录</RouterLink>
        </p>
      </form>
    </AuthCard>

    <template #footer>
      <SiteFooter />
    </template>
  </AuthPageLayout>
</template>

<style scoped>
.auth-header__login {
  color: var(--mall-brand);
  font-weight: 600;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 22px;
}

.auth-field label {
  display: block;
  margin-bottom: 7px;
  color: var(--mall-ink-2);
  font-size: 12px;
  font-weight: 600;
}

.auth-field__optional {
  margin-left: 4px;
  color: var(--mall-ink-4);
  font-weight: 400;
}

.auth-field__control {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
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

.password-meter {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
}

.password-meter__bar {
  flex: 1;
  height: 4px;
  border-radius: 999px;
  background: var(--mall-line);
  transition: background-color 0.2s var(--mall-ease);
}

.password-meter__label {
  width: 34px;
  color: var(--mall-ink-3);
  font-size: 11px;
  text-align: right;
}

.password-meter.is-weak .password-meter__bar:nth-child(1) {
  background: #e05252;
}

.password-meter.is-medium .password-meter__bar:nth-child(-n + 2) {
  background: #e6a23c;
}

.password-meter.is-strong .password-meter__bar {
  background: var(--mall-success);
}

.password-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 8px;
}

.password-hints li {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--mall-ink-3);
  font-size: 11px;
}

.password-hints li.is-passed {
  color: var(--mall-success);
}

.auth-field--agree {
  margin-top: 2px;
}

.auth-check {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--mall-ink-2);
  font-size: 12px;
  line-height: 1.7;
  cursor: pointer;
}

.auth-check input {
  flex-shrink: 0;
  width: 15px;
  height: 15px;
  margin-top: 3px;
  accent-color: var(--mall-brand);
}

.auth-check a {
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

.auth-submit {
  margin-top: 4px;
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