<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getUseMock } from '@/mock'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const useMock = getUseMock()

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 种子账号（见 sql/07_seed_user_auth.sql）；buyer01 刻意列出用于验证"无后台权限"拦截 */
const demoAccounts = [
  { username: 'operator01', password: 'Operator@123456', label: '运营', type: 'primary' },
  { username: 'admin', password: 'Admin@123456', label: '管理员', type: 'danger' },
  { username: 'buyer01', password: 'Buyer@123456', label: '买家（应被拒绝）', type: 'info' },
]

onMounted(() => {
  if (route.query.denied) {
    ElMessage.warning('该账号无后台访问权限（需要 运营 / 客服 / 管理员 角色）')
  }
})

function fill(account) {
  form.username = account.username
  form.password = account.password
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workbench'
    router.replace(redirect)
  } catch (e) {
    // 后端错误码文案优先（31004 账号或密码错误 / 20003 无权限 / 31005 锁定）
    ElMessage.error(e?.message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-brand">
        <div class="brand-mark">M</div>
        <h1>商场管理后台</h1>
        <p>商品 · 库存 · 订单 · 退款 的统一运营入口</p>
        <ul class="feature-list">
          <li>商品上下架与 SKU 管理</li>
          <li>库存调整与流水追溯</li>
          <li>订单发货与退款处理</li>
        </ul>
      </div>

      <div class="login-form">
        <h2>账号登录</h2>
        <p class="sub">请使用运营 / 客服 / 管理员账号登录</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              placeholder="请输入密码"
              show-password
              @keyup.enter="onSubmit"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="onSubmit"
          >
            登 录
          </el-button>
        </el-form>

        <div class="demo-box">
          <div class="demo-title">
            快速填充种子账号
            <el-tag v-if="useMock" size="small" type="warning" effect="plain">演示模式</el-tag>
          </div>
          <div class="demo-tags">
            <el-button
              v-for="account in demoAccounts"
              :key="account.username"
              size="small"
              :type="account.type"
              plain
              @click="fill(account)"
            >
              {{ account.label }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fff4ee 0%, #ffe7d9 45%, #ffd9c2 100%);
}

.login-box {
  display: flex;
  width: 760px;
  max-width: calc(100% - 32px);
  min-height: 420px;
  background: var(--bg-white);
  border-radius: 8px;
  box-shadow: 0 12px 40px rgba(255, 80, 0, 0.12);
  overflow: hidden;
}

.login-brand {
  flex: 0 0 320px;
  padding: 36px 28px;
  background: linear-gradient(160deg, #ff5000, #ff8b3d);
  color: #fff;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
}

.login-brand h1 {
  font-size: 21px;
  margin: 18px 0 6px;
}

.login-brand p {
  margin: 0;
  font-size: 13px;
  opacity: 0.92;
  line-height: 20px;
}

.feature-list {
  margin: 22px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 26px;
  opacity: 0.95;
}

.login-form {
  flex: 1 1 auto;
  padding: 34px 32px;
}

.login-form h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

.sub {
  margin: 0 0 20px;
  color: var(--text-secondary);
  font-size: 13px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.demo-box {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--border-color);
}

.demo-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.demo-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.demo-tags .el-button + .el-button {
  margin-left: 0;
}
</style>
