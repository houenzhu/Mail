const FALLBACKS = {
  digital: '/images/products/headphones.jpg',
  headphones: '/images/products/headphones.jpg',
  keyboard: '/images/products/keyboard.jpg',
  camera: '/images/products/camera.jpg',
  apparel: '/images/products/tshirt.jpg',
  tshirt: '/images/products/tshirt.jpg',
  sneakers: '/images/products/sneakers.jpg',
  bag: '/images/products/bag.jpg',
  watch: '/images/products/watch.jpg',
  home: '/images/products/chair.jpg',
  chair: '/images/products/chair.jpg',
  coffee: '/images/products/coffee.jpg',
  beauty: '/images/products/skincare.jpg',
  skincare: '/images/products/skincare.jpg',
  lipstick: '/images/products/lipstick.jpg',
  perfume: '/images/products/perfume.jpg',
  default: '/images/products/headphones.jpg',
}

/**
 * 瀑布流图片比例：按品类固定取值，保证同一类商品在不同页面保持一致。
 * 只使用 1:1、4:5、3:4 三档，避免比例差异过大显得杂乱。
 */
const IMAGE_RATIOS = {
  headphones: '1',
  keyboard: '4 / 5',
  camera: '3 / 4',
  apparel: '4 / 5',
  tshirt: '1',
  sneakers: '1',
  bag: '4 / 5',
  watch: '3 / 4',
  chair: '1',
  home: '4 / 5',
  coffee: '4 / 5',
  beauty: '3 / 4',
  skincare: '4 / 5',
  lipstick: '3 / 4',
  perfume: '3 / 4',
  digital: '1',
  default: '1',
}

const NAME_RULES = [
  [/耳机|降噪/, 'headphones'],
  [/键盘/, 'keyboard'],
  [/相机|微单|镜头/, 'camera'],
  [/T恤|T 恤|卫衣|衬衫|外套|裤/, 'tshirt'],
  [/鞋|跑鞋|运动鞋/, 'sneakers'],
  [/包|托特|背包/, 'bag'],
  [/腕表|手表/, 'watch'],
  [/椅|沙发|桌|床|家居|收纳/, 'chair'],
  [/咖啡|茶|食品/, 'coffee'],
  [/面霜|护肤|精华|洁面/, 'skincare'],
  [/唇|口红|眼影|粉底/, 'lipstick'],
  [/香水/, 'perfume'],
]

export function resolveImage(url, fallbackKey = 'default') {
  if (typeof url !== 'string' || !url.trim()) return fallbackFor(fallbackKey)
  if (/^(https?:)?\/\//i.test(url)) {
    // 演示库里的 img.example.com 是占位域名，不应发真实网络请求
    if (/img\.example\.com/i.test(url)) return fallbackFor(fallbackKey)
    return url
  }
  if (url.startsWith('/')) return url
  return fallbackFor(fallbackKey)
}

export function fallbackFor(fallbackKey = 'default') {
  return FALLBACKS[fallbackKey] || FALLBACKS.default
}

export function inferImageKey(name = '', categoryId) {
  const text = String(name)
  for (const [pattern, key] of NAME_RULES) {
    if (pattern.test(text)) return key
  }
  if (categoryId === 2) return 'apparel'
  if (categoryId === 3) return 'headphones'
  if (categoryId === 4) return 'beauty'
  return 'digital'
}

export function ratioForImageKey(key = 'default') {
  return IMAGE_RATIOS[key] || IMAGE_RATIOS.default
}
