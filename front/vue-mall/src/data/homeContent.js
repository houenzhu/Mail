import { inferImageKey, ratioForImageKey } from '@/utils/media'

export const heroSlides = [
  {
    id: 'season',
    eyebrow: '秋冬焕新季',
    title: '把家，住成喜欢的样子',
    subtitle: '柔软织物、暖调灯光与舒适座椅，给日常一点松弛感。',
    desc: '柔软织物、暖调灯光与舒适座椅，给日常一点松弛感。',
    cta: '逛逛家居好物',
    query: '家居',
    image: '/images/products/chair.jpg',
    imageAlt: '暖调客厅里的休闲单椅',
    theme: 'warm',
    stats: [
      { label: '品质家居', value: '低至 5 折' },
      { label: '满减券', value: '每满 300 减 40' },
    ],
  },
  {
    id: 'beauty',
    eyebrow: '秋日氛围感',
    title: '温柔一点，也很好看',
    subtitle: '低饱和配色与轻薄质地，通勤、约会都自在。',
    desc: '低饱和配色与轻薄质地，通勤、约会都自在。',
    cta: '挑选美妆好物',
    query: '面霜',
    image: '/images/products/skincare.jpg',
    imageAlt: '暖光下的护肤好物',
    theme: 'blush',
    stats: [
      { label: '护肤精选', value: '买二免一' },
      { label: '会员礼', value: '赠旅行装' },
    ],
  },
  {
    id: 'digital',
    eyebrow: '数码焕新',
    title: '把效率，装进口袋',
    subtitle: '轻量装备与长续航，陪你从早忙到晚。',
    desc: '轻量装备与长续航，陪你从早忙到晚。',
    cta: '查看数码清单',
    query: '耳机',
    image: '/images/products/headphones.jpg',
    imageAlt: '黄色背景上的降噪耳机',
    theme: 'tech',
    stats: [
      { label: '以旧换新', value: '最高补贴 600' },
      { label: '延保服务', value: '低至 9.9 元' },
    ],
  },
]

export const navCategories = [
  { id: 'digital', name: '电脑 / 配件', icon: 'monitor', keyword: '耳机' },
  { id: 'phone', name: '手机 / 通讯', icon: 'phone', keyword: '手机' },
  { id: 'appliance', name: '家电 / 家居', icon: 'home', keyword: '家居' },
  { id: 'apparel', name: '女装 / 男装', icon: 'shirt', keyword: 'T 恤' },
  { id: 'shoes', name: '鞋靴 / 运动', icon: 'shoe', keyword: '鞋' },
  { id: 'beauty', name: '美妆 / 个护', icon: 'sparkle', keyword: '面霜' },
  { id: 'baby', name: '母婴 / 玩具', icon: 'baby', keyword: '玩具' },
  { id: 'food', name: '食品 / 生鲜', icon: 'food', keyword: '咖啡' },
  { id: 'furniture', name: '家装 / 建材', icon: 'lamp', keyword: '家居' },
  { id: 'auto', name: '汽车 / 用品', icon: 'car', keyword: '车载' },
]

export const servicePromises = [
  { id: 'authentic', icon: 'shield-check', title: '正品保障', desc: '品牌直供 · 假一赔十' },
  { id: 'delivery', icon: 'truck', title: '极速发货', desc: '48 小时内发出' },
  { id: 'return', icon: 'refresh', title: '七天无理由', desc: '退换无忧 · 上门取件' },
  { id: 'service', icon: 'headset', title: '贴心客服', desc: '9:00 - 22:00 在线' },
]

export const quickActivities = [
  { id: 'home', title: '品质家居', subtitle: '舒适生活提案', theme: 'orange', image: '/images/products/chair.jpg', query: '家居' },
  { id: 'beauty', title: '精致美妆', subtitle: '秋日氛围感', theme: 'pink', image: '/images/products/lipstick.jpg', query: '面霜' },
  { id: 'digital', title: '数码焕新', subtitle: '效率装备清单', theme: 'gold', image: '/images/products/keyboard.jpg', query: '耳机' },
  { id: 'daily', title: '生活百货', subtitle: '把日常过好', theme: 'blue', image: '/images/products/coffee.jpg', query: '咖啡' },
]

export const featureItems = [
  { icon: 'shield-check', title: '正品保障', desc: '品牌直供 · 假一赔十' },
  { icon: 'truck', title: '极速发货', desc: '48 小时内发出' },
  { icon: 'refresh', title: '七天无理由', desc: '退换无忧 · 上门取件' },
  { icon: 'headset', title: '贴心客服', desc: '9:00 - 22:00 在线' },
]

export const showcaseSections = [
  {
    id: 'digital',
    title: '数码潮玩',
    subtitle: '把热爱装进口袋',
    image: '/images/products/headphones.jpg',
    tone: 'slate',
    query: '耳机',
    tags: ['降噪耳机', '机械键盘', '掌上影音'],
  },
  {
    id: 'apparel',
    title: '自在穿搭',
    subtitle: '基础款，也有好质感',
    image: '/images/products/sneakers.jpg',
    tone: 'sand',
    query: '鞋',
    tags: ['纯棉T恤', '轻量跑鞋', '通勤包袋'],
  },
  {
    id: 'home',
    title: '理想家',
    subtitle: '让每一平米都舒服',
    image: '/images/products/chair.jpg',
    tone: 'sage',
    query: '家居',
    tags: ['休闲单椅', '香氛蜡烛', '餐具套装'],
  },
  {
    id: 'beauty',
    title: '悦己时刻',
    subtitle: '温柔地照顾自己',
    image: '/images/products/skincare.jpg',
    tone: 'rose',
    query: '面霜',
    tags: ['保湿面霜', '丝绒唇釉', '木质香水'],
  },
]

const demoRawProducts = [
  { id: 101, name: '云感降噪头戴耳机', subtitle: '40 小时续航 / 自适应降噪 / 轻至 238g', categoryId: 1, mainImage: '/images/products/headphones.jpg', priceMin: 499, priceMax: 599, sales: 12600, badge: '新品' },
  { id: 102, name: '轻量透气跑步鞋', subtitle: '回弹中底 / 防滑大底 / 三色可选', categoryId: 2, mainImage: '/images/products/sneakers.jpg', priceMin: 269, priceMax: 329, sales: 8600, badge: '热卖' },
  { id: 103, name: '奶油风休闲单椅', subtitle: '高回弹坐垫 / 实木椅脚 / 久坐不累', categoryId: 3, mainImage: '/images/products/chair.jpg', priceMin: 899, priceMax: 1099, sales: 1200, badge: '精选' },
  { id: 104, name: '屏障修护保湿面霜', subtitle: '神经酰胺 / 轻盈不黏腻 / 敏感肌友好', categoryId: 4, mainImage: '/images/products/skincare.jpg', priceMin: 189, priceMax: 189, sales: 23000, badge: '回购榜' },
  { id: 105, name: '复古微单相机套装', subtitle: '4K 视频 / 五轴防抖 / 含定焦镜头', categoryId: 1, mainImage: '/images/products/camera.jpg', priceMin: 4299, priceMax: 4699, sales: 680, badge: '' },
  { id: 106, name: '手冲咖啡礼盒', subtitle: '中深烘焙 / 坚果可可调 / 6 种风味', categoryId: 5, mainImage: '/images/products/coffee.jpg', priceMin: 129, priceMax: 159, sales: 5400, badge: '礼盒' },
  { id: 107, name: '通勤真皮托特包', subtitle: '头层牛皮 / 大容量 / 可放 14 寸电脑', categoryId: 2, mainImage: '/images/products/bag.jpg', priceMin: 459, priceMax: 459, sales: 3100, badge: '' },
  { id: 108, name: '丝绒哑光唇釉', subtitle: '低饱和玫瑰色 / 轻薄显色 / 不拔干', categoryId: 4, mainImage: '/images/products/lipstick.jpg', priceMin: 99, priceMax: 129, sales: 45000, badge: '爆款' },
  { id: 109, name: '极简腕表 38mm', subtitle: '蓝宝石镜面 / 真皮表带 / 50m 防水', categoryId: 2, mainImage: '/images/products/watch.jpg', priceMin: 799, priceMax: 899, sales: 960, badge: '新品' },
  { id: 110, name: '雪松木质淡香水', subtitle: '前调佛手柑 / 中调雪松 / 尾调琥珀', categoryId: 4, mainImage: '/images/products/perfume.jpg', priceMin: 359, priceMax: 359, sales: 7200, badge: '' },
  { id: 111, name: '重磅纯棉基础 T 恤', subtitle: '260g 精梳棉 / 不易变形 / 四色可选', categoryId: 2, mainImage: '/images/products/tshirt.jpg', priceMin: 89, priceMax: 109, sales: 18000, badge: '基础款' },
  { id: 112, name: '客制化机械键盘', subtitle: 'Gasket 结构 / 热插拔 / 三模连接', categoryId: 1, mainImage: '/images/products/keyboard.jpg', priceMin: 399, priceMax: 599, sales: 2400, badge: '客制化' },
  { id: 113, name: '便携陶瓷咖啡杯', subtitle: '手工釉面 / 350ml 两只装 / 可进洗碗机', categoryId: 5, mainImage: '/images/products/coffee.jpg', priceMin: 69, priceMax: 89, sales: 3300, badge: '新品' },
]

export const demoProducts = demoRawProducts.map((raw) => {
  const priceMin = Number(raw.priceMin) || 0
  const priceMax = Number(raw.priceMax) || priceMin
  const priceRange = priceMin === priceMax ? `¥${priceMin}` : `¥${priceMin} - ${priceMax}`
  const salesText = raw.sales >= 10000
    ? `${(raw.sales / 10000).toFixed(1).replace(/\.0$/, '')}万+人付款`
    : raw.sales >= 1000
      ? `${(raw.sales / 1000).toFixed(1).replace(/\.0$/, '')}千+人付款`
      : `${raw.sales}人付款`
  const imageKey = inferImageKey(raw.name, raw.categoryId)
  return {
    id: raw.id,
    title: raw.name,
    subtitle: raw.subtitle,
    image: raw.mainImage,
    imageKey,
    imageRatio: ratioForImageKey(imageKey),
    priceRange,
    priceMin,
    priceMax,
    salesText,
    tag: raw.badge,
    detailPath: `/feature/product-detail?id=${raw.id}`,
  }
})

export const footerColumns = [
  {
    title: '购物指南',
    links: ['购物流程', '会员介绍', '常见问题', '联系客服'],
  },
  {
    title: '配送方式',
    links: ['配送服务', '运费说明', '自提点查询', '海外配送'],
  },
  {
    title: '支付方式',
    links: ['在线支付', '分期付款', '发票说明', '安全中心'],
  },
  {
    title: '售后服务',
    links: ['退款说明', '退换货政策', '价格保护', '投诉建议'],
  },
]

export const headerShortcuts = [
  { label: '我的订单', feature: 'orders' },
  { label: '我的收藏', feature: 'favorites' },
  { label: '购物车', feature: 'cart' },
  { label: '免费开店', feature: 'seller' },
  { label: '商家中心', feature: 'merchant' },
  { label: '帮助中心', feature: 'help' },
]




