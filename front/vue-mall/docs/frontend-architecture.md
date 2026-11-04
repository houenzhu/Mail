# 买家端前端架构与交付说明

> 版本：0.2.0 · 阶段二（认证与账户中心）  
> 技术栈：Vue 3.5 + Vite 8 + Pinia + Vue Router  
> 设计基线：淘宝 PC 首页的信息密度与视觉节奏，不复制其品牌素材与业务数据。

## 1. 目标与边界

阶段二交付一个**可运行、可复用、可逐步接真实业务**的 C 端首页与登录体系：

- 已完成：全局页头、搜索、分类导航、首屏 Banner、活动入口、猜你喜欢瀑布流、分类展示、服务承诺、页脚、悬浮工具条。
- 已完成：商品接口适配层，真实网关不可用时自动回退到本地演示数据，页面不白屏。
- 已完成：响应式布局（桌面 / 平板 / 手机）与基础可访问性（语义标签、键盘焦点、跳过导航、可见焦点环）。
- 已完成：登录 / 注册、用户态持久化、跨标签刷新互斥、路由守卫与账户中心骨架。
- 不在本阶段：购物车写入、下单支付、商品详情业务页、地址与订单管理。相关入口统一引导到「功能预告」页，避免假按钮。

## 2. 信息架构

```
/                      首页
/search?q=关键词        搜索结果
/login                  登录（支持 redirect 与记住用户名）
/register               注册（昵称选填、密码强度提示）
/account                账户中心（requiresAuth）
/feature/:feature       功能预告（购物车、订单等未上线能力）
/:pathMatch(.*)*        404
```

首页自上而下：

1. `SiteHeader`：顶部工具条 + 品牌区 + 搜索区，已接入登录 / 账户菜单
2. `HomeCategoryNav`：左侧一级分类，右侧主 Banner 与活动卡
3. `HomeFeatureStrip`：品质 / 配送 / 售后 / 保障承诺
4. `HomeCategoryShowcase`：四大生活场景入口
5. `HomeRecommend`：猜你喜欢，复用 `MasonryGrid` 瀑布流（桌面 4 列 / 平板 3 列 / 手机 2 列），首卡横跨两列作为精选位
6. `SiteFooter`：帮助中心、服务条款、备案信息
7. `FloatingToolbar`：返回顶部、客服、购物车占位与账户入口

## 3. 组件分层

```
src/
├─ api/                    # 只依赖 fetch，可替换为 axios，不耦合视图
│  ├─ client.js            # 统一响应解包、超时、错误规范、401 刷新重放
│  ├─ auth.js              # 登录、注册、当前用户、注销与单飞刷新
│  ├─ refreshLock.js       # 跨标签页刷新互斥锁
│  ├─ tokenStorage.js      # 买家端令牌读写与跨标签同步
│  └─ product.js           # 商品/分类/品牌查询
├─ assets/styles/          # 设计令牌 + 全局基础样式
├─ components/
│  ├─ common/              # 无业务语义，可跨页面复用
│  │  ├─ SmartImage.vue    # 懒加载 / 失败兜底 / 占位骨架 / 自定义取景位置
│  │  ├─ MasonryGrid.vue   # 通用瀑布流容器，不耦合商品业务
│  │  ├─ AppIcon.vue       # 内联 SVG 图标注册表
│  │  ├─ BaseButton.vue    # 主/次/文字按钮，统一尺寸与状态
│  │  ├─ BaseTag.vue       # 标签，支持多种色调
│  │  ├─ SectionHeading.vue
│  │  └─ EmptyState.vue
│  ├─ auth/
│  │  ├─ AuthPageLayout.vue # 登录注册共用品牌头、说明区与主布局
│  │  └─ AuthCard.vue       # 登录注册共用卡片头
│  ├─ layout/
│  │  ├─ SiteHeader.vue
│  │  ├─ SearchBar.vue
│  │  ├─ SiteFooter.vue
│  │  └─ FloatingToolbar.vue
│  ├─ product/
│  │  ├─ ProductCard.vue   # 商品卡片，支持网格 / 瀑布流 / 精选位
│  │  └─ ProductGrid.vue   # 搜索等需要行序稳定的规整网格
│  ├─ home/
│  │  ├─ HomeCategoryNav.vue
│  │  ├─ HeroBanner.vue
│  │  ├─ HomeFeatureStrip.vue
│  │  ├─ HomeCategoryShowcase.vue
│  │  └─ HomeRecommend.vue
│  └─ ...
├─ composables/
│  ├─ useHomeContent.js    # 首页聚合数据、加载态与错误态
│  ├─ useAuthForm.js       # 登录 / 注册表单状态、校验与密码强度
│  ├─ useCarousel.js       # 自动轮播，含暂停、键盘、减少动效
│  └─ useMasonryLayout.js  # 瀑布流列数 / 高度 / 精选位布局状态
├─ data/                   # 演示数据（与接口 DTO 同形）
├─ stores/
│  └─ user.js              # 用户资料、令牌状态、恢复、注销与会话失效
├─ utils/
│  ├─ format.js            # 金额、销量、文本截断
│  ├─ authPreferences.js   # 记住用户名
│  ├─ media.js             # 图片 URL、比例、兜底与色彩占位
│  └─ masonry.js           # 纯函数瀑布流装箱算法
└─ views/
   ├─ HomeView.vue
   ├─ SearchView.vue
   ├─ LoginView.vue
   ├─ RegisterView.vue
   ├─ AccountView.vue
   ├─ FeatureView.vue
   └─ NotFoundView.vue
```

### 组件契约原则

- **展示组件不取数**：`ProductCard` 只接收 `product`，事件向外抛；便于复用与单测。
- **数据聚合在 composable**：`useHomeContent` 负责并发请求、降级、重试，视图不关心接口细节。
- **样式隔离**：组件使用 `scoped`，尺寸与间距统一走 CSS 变量；禁止在组件里写死品牌色。
- **可替换适配层**：接口字段到视图模型在 `api/product.js` 完成映射（如 `priceMin/priceMax` → `priceRange`），更换后端字段只改一处。
- **无隐藏交互**：所有可点击元素有明确 hover/focus 态；未上线能力显式跳预告页，不使用 `href="#"`。

### 瀑布流契约

`MasonryGrid` 是通用容器，不理解商品字段：

| Props | 类型 | 说明 |
| :--- | :--- | :--- |
| `items` | `Array` | 需要每个元素有稳定 `id` 或 `key` |
| `breakpoints` | `Array` | 按容器宽度决定列数，默认 2 / 3 / 4 列 |
| `featuredIndex` | `Number` | 精选卡索引，默认 `-1` 表示不启用 |
| `featuredSpan` | `Number` | 精选卡跨列数，列数不足时自动收缩 |
| `gap` / `mobileGap` | `Number` | 桌面与移动端列间距 |
| `ariaLabel` | `String` | 无障碍区域标签 |

布局规则：

- DOM 顺序始终等于原始数组顺序，Tab 与读屏不被视觉重排打乱。
- `packMasonry` 是纯函数装箱算法，采用少量前瞻减少列尾落差。
- 图片加载、字体就绪、容器宽度变化、数据增删都会触发重排。
- 未测量前按商品 `imageRatio` 估算高度，降低首帧跳动。
- 首页实际验收：1440 / 1024 / 390 宽度分别为 4 / 3 / 2 列，尾列落差 136 / 97 / 44px，均小于一张普通卡高。

## 4. 设计令牌

| 令牌 | 值 | 用途 |
| :--- | :--- | :--- |
| `--mall-brand` | `#ff5000` | 主品牌橙 |
| `--mall-brand-deep` | `#e63f00` | hover / 强调 |
| `--mall-ink` | `#1f1f1f` | 主文字 |
| `--mall-ink-2` | `#666` | 次级文字 |
| `--mall-ink-3` | `#999` | 辅助信息 |
| `--mall-line` | `#ededed` | 分割线 |
| `--mall-bg` | `#f5f5f5` | 页面底色 |
| `--mall-radius` | `12px` | 卡片圆角 |
| `--mall-container` | `1200px` | 内容最大宽度 |

字号基线 14px，标题 16/18/22/30px，遵循 4px 间距体系。

## 5. 数据契约

`GET /api/product/page`（公开）：

```json
{
  "code": 0,
  "data": {
    "total": 3,
    "pageNo": 1,
    "pageSize": 12,
    "list": [{
      "id": 1,
      "name": "无线降噪耳机 Pro",
      "subtitle": "40小时续航 / 主动降噪",
      "mainImage": "https://...",
      "priceMin": 499.00,
      "priceMax": 519.00,
      "sales": 120,
      "status": 1
    }]
  }
}
```

- 仅展示 `status === 1` 的上架商品。
- `priceMin === priceMax` 时展示单价；否则展示区间。
- 图片缺失或加载失败使用本地兜底图与主题色占位，不留破图。
- 售罄占位商品不进首页推荐位。

前端适配后的视图模型：

```js
{
  id, title, subtitle, image, imageKey, imageRatio,
  priceRange, priceMin, priceMax, salesText, badge,
  detailPath: '/feature/product-detail?id=...'
}
```

第三阶段商品详情落地后，仅需替换 `toProductView` 中的 `detailPath`。

### 认证契约

| 接口 | 说明 |
| :--- | :--- |
| `POST /api/auth/register` | 用户名、密码、手机号；昵称选填，最长 32 位 |
| `POST /api/auth/login` | 返回用户基础信息、accessToken（300s）、refreshToken（7d） |
| `POST /api/auth/refresh` | 刷新令牌一次一换；旧令牌重放会触发后端全端下线 |
| `GET /api/auth/me` | 返回脱敏手机号、角色、状态与上次登录时间 |
| `POST /api/auth/logout` | 全端下线并作废刷新令牌 |

关键错误码：`31002` 手机号已注册、`31003` 用户名已存在、`31004` 账号或密码错误、`31005` 账号锁定、`31006` 账号停用。

### 令牌生命周期

1. 登录成功后写入 `mall_buyer_access_token` / `mall_buyer_refresh_token`。
2. 路由守卫发现「有令牌无资料」时调用 `/auth/me`。
3. 受保护接口返回 401 / `20001` / `20002` 时进入刷新流程。
4. 同一标签页内并发请求共享一个刷新 Promise；跨标签页通过 Web Locks 互斥。
5. 拿到锁后先比对 accessToken；若其他标签已刷新过，则直接复用新令牌，不再请求 `/auth/refresh`。
6. 刷新失败或注销后清空两个令牌并同步跨标签登录态。

### 路由守卫

- `requiresAuth`：未登录跳 `/login?redirect=<fullPath>&reason=required`。
- `guestOnly`：已登录访问登录 / 注册时回到 `redirect` 或首页。
- `redirect` 只接受站内绝对路径，拒绝 `//` 开头的开放重定向。

## 6. 状态与降级

| 场景 | 行为 |
| :--- | :--- |
| 接口成功 | 渲染真实商品；真实不足 13 件时用演示数据补足 |
| 接口失败 / 超时 6s | 展示本地演示商品，并显示轻量提示“演示商品 · 接口暂不可用” |
| 图片 404 | 首帧显示底色占位，失败后切到同品类兜底图 |
| 减少动效偏好 | 关闭自动轮播、瀑布流过渡与高度动画 |
| 窄屏 | 分类侧栏收起为横向滚动；瀑布流降为 2 列，精选卡仍占满两列 |

## 7. 可访问性与性能

- 图片统一 `loading="lazy"`（首屏 Banner 与前两张推荐卡为 `eager`），显式宽高比防抖动。
- 轮播支持左右键、暂停按钮；`aria-live` 只在用户手动切换时播报。
- 瀑布流保持原始 DOM 顺序，避免读屏顺序与视觉顺序不一致。
- 搜索框有可见 `<label>`、`Enter` 提交、空关键词不跳转。
- 首屏字体使用系统字体栈，避免额外阻塞；图标为内联 SVG，无图标字体请求。
- 构建产物按路由拆分；首页只依赖 Vue 运行时与自身代码。

## 8. 阶段规划

| 阶段 | 范围 | 验收标准 |
| :--- | :--- | :--- | :--- |
| 一 | 首页、公共布局、商品接口适配、搜索入口、瀑布流 | 桌面与移动端可正常浏览；接口失败可降级；构建通过 | 已完成 |
| 二 | 登录 / 注册、用户态、账户中心骨架 | 表单校验、令牌持久化、路由守卫、登出、布局无溢出 | 已完成 |
| 三 | 商品详情、SKU 选择、购物车 | 规格联动、库存校验、购物车持久化 |
| 四 | 结算、订单、支付回跳 | 地址、运费、订单状态、支付结果轮询 |
| 五 | 评价、客服、营销活动 | 与后端营销/客服模块对齐 |

## 9. 验证命令

```bash
npm run build
npm run dev
node scripts/probe.mjs http://localhost:5175 1440
node scripts/probe.mjs http://localhost:5175 1024
node scripts/probe.mjs http://localhost:5175 390
npm run verify -- http://localhost:5175
npm run verify:auth -- http://localhost:5175
```

截图输出在 `verify-out/`，覆盖首页、搜索、登录、注册、账户中心，以及 1440px 桌面与 390px 移动端，并检查控制台异常。

## 10. 阶段二验收结论

- `npm run build` 通过。
- `npm run verify -- http://127.0.0.1:5175` 通过，首页与搜索未发现 JS / 控制台错误。
- 1440 / 1024 / 390 瀑布流分别为 4 / 3 / 2 列，无横向溢出。
- `npm run verify:auth -- http://127.0.0.1:5175` 通过 16/16：守卫、错误密码、登录资料、刷新恢复、无感续期、账户菜单、移动布局、注销与注册校验均通过。
