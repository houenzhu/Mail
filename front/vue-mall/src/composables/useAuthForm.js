import { computed, reactive, ref, watch } from 'vue'

const USERNAME_PATTERN = /^[a-zA-Z0-9_]{4,32}$/
const PHONE_PATTERN = /^1[3-9]\d{9}$/

export const PASSWORD_RULES = [
  { id: 'length', label: '8-32 位字符', test: (value) => value.length >= 8 && value.length <= 32 },
  { id: 'letter', label: '包含字母', test: (value) => /[A-Za-z]/.test(value) },
  { id: 'digit', label: '包含数字', test: (value) => /\d/.test(value) },
]

export function passwordScore(value = '') {
  return PASSWORD_RULES.filter((rule) => rule.test(value)).length
}

export function validateUsername(value = '') {
  if (!value.trim()) return '请输入用户名'
  if (!USERNAME_PATTERN.test(value.trim())) return '用户名需为 4~32 位字母、数字或下划线'
  return ''
}

export function validatePassword(value = '') {
  if (!value) return '请输入密码'
  if (value.length < 8 || value.length > 32) return '密码需为 8~32 位'
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) return '密码需同时包含字母和数字'
  return ''
}

export function validatePhone(value = '') {
  if (!value.trim()) return '请输入手机号'
  if (!PHONE_PATTERN.test(value.trim())) return '请输入 11 位中国大陆手机号'
  return ''
}

export function validateNickname(value = '') {
  if (value.trim().length > 32) return '昵称不能超过 32 位'
  return ''
}

/**
 * 登录 / 注册共用表单状态：
 * - 失焦后才展示字段错误，输入时只清除对应错误，避免用户刚敲第一个字就报红；
 * - 提交前统一校验并聚焦第一个错误字段；
 * - 密码强度规则独立导出，注册页直接复用。
 */
export function useAuthForm(mode = 'login') {
  const isRegister = computed(() => mode === 'register')
  const form = reactive({
    username: '',
    password: '',
    phone: '',
    nickname: '',
    agree: false,
    remember: true,
  })
  const errors = reactive({
    username: '',
    password: '',
    phone: '',
    nickname: '',
    agree: '',
    form: '',
  })
  const touched = reactive({ username: false, password: false, phone: false, nickname: false })
  const submitting = ref(false)
  const passwordVisible = ref(false)

  const passwordStrength = computed(() => passwordScore(form.password))
  const passwordHints = computed(() =>
    PASSWORD_RULES.map((rule) => ({ ...rule, passed: rule.test(form.password) })),
  )

  const validators = computed(() => ({
    username: () => validateUsername(form.username),
    password: () => validatePassword(form.password),
    phone: () => validatePhone(form.phone),
    nickname: () => validateNickname(form.nickname),
  }))

  function validateField(field) {
    const validator = validators.value[field]
    if (!validator) return ''
    errors[field] = touched[field] ? validator() : ''
    return validator()
  }

  function touch(field) {
    touched[field] = true
    validateField(field)
  }

  function validateAll() {
    const next = {
      username: validateUsername(form.username),
      password: validatePassword(form.password),
      phone: isRegister.value ? validatePhone(form.phone) : '',
      nickname: isRegister.value ? validateNickname(form.nickname) : '',
      agree: isRegister.value && !form.agree ? '请先阅读并同意用户协议与隐私政策' : '',
      form: '',
    }
    Object.assign(errors, next)
    const firstError = Object.keys(next).find((key) => next[key])
    return firstError || ''
  }

  watch(
    () => form.username,
    () => {
      if (touched.username) validateField('username')
      errors.form = ''
    },
  )
  watch(
    () => form.password,
    () => {
      if (touched.password) validateField('password')
      errors.form = ''
    },
  )
  watch(
    () => form.phone,
    () => {
      if (touched.phone) validateField('phone')
      errors.form = ''
    },
  )
  watch(
    () => form.nickname,
    () => {
      if (touched.nickname) validateField('nickname')
      errors.form = ''
    },
  )
  watch(
    () => form.agree,
    () => {
      if (form.agree) errors.agree = ''
    },
  )

  function resetFormError() {
    errors.form = ''
  }

  return {
    form,
    errors,
    touched,
    submitting,
    passwordVisible,
    passwordStrength,
    passwordHints,
    isRegister,
    validateField,
    touch,
    validateAll,
    resetFormError,
  }
}
