const REMEMBERED_USERNAME_KEY = 'mall_buyer_remembered_username'

export function getRememberedUsername() {
  try {
    return localStorage.getItem(REMEMBERED_USERNAME_KEY) || ''
  } catch {
    return ''
  }
}

export function setRememberedUsername(username) {
  try {
    if (username) localStorage.setItem(REMEMBERED_USERNAME_KEY, username)
    else localStorage.removeItem(REMEMBERED_USERNAME_KEY)
  } catch {
    // 隐私模式下忽略，不影响登录本身
  }
}

export function clearRememberedUsername() {
  setRememberedUsername('')
}