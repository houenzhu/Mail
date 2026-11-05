# 买家端前端（`vue-mall`）

好物集商城 C 端，基于 Vue 3 + Vite + Pinia + Vue Router。当前完成**阶段三：商品详情、SKU 选择与购物车**；首页、搜索、认证与购物车均接入真实网关接口，首页商品接口异常时仍保留演示数据降级。

## 快速开始

```bash
npm install
npm run dev         # http://127.0.0.1:5175，/api 代理到 127.0.0.1:10000
npm run build       # 生产构建
npm run verify      # 首页 / 搜索 UI 回归（需先启动 dev）
npm run verify:auth # 登录 / 注册 / 账户中心 UI 验收（需先启动 dev 与后端）
npm run verify:cart # 商品详情 / SKU / 购物车 UI 验收（需先启动 dev 与后端）
npm run verify:demo # 首页演示商品、旧详情链接与真实详情 UI 验收（需先启动 dev 与后端）
```

## 已实现页面

| 路由 | 页面 | 状态 |
| :--- | :--- | :--- |
| `/` | 首页（分类导航、轮播、活动入口、服务保障、猜你喜欢、场景展示） | 已完成 |
| `/search?q=` | 搜索结果（真实接口、综合/销量排序、空态与错误态） | 已完成 |
| `/product/:id` | 商品详情（实时价格、规格联动、库存、数量、加购、看了又看） | 已完成 |
| `/cart` | 购物车（登录守卫、数量、勾选、全选、删除、去结算） | 已完成 |
| `/checkout` | 确认订单（地址选择/新增/编辑、明细、备注、幂等提交） | 已完成 |
| `/order/list` | 我的订单（状态 Tab、分页、明细卡片、去支付） | 已完成 |
| `/order/:orderNo` | 订单详情（收货/物流、金额、状态时间线、支付倒计时、取消/确认收货） | 已完成 |
| `/pay/:orderNo` | 收银台（创建支付单、渠道参数、Mock 模拟支付、轮询状态） | 已完成 |
| `/login` | 登录（校验、错误提示、redirect、记住用户名） | 已完成 |
| `/register` | 注册（校验、昵称选填、密码强度、协议确认） | 已完成 |
| `/account` | 账户中心（登录守卫、身份头、快捷入口、资料） | 已完成 |
| `/feature/:feature` | 功能预告（订单、收藏等未上线入口） | 已完成 |
| `/feature/product-detail?id=` | 旧详情链接兼容，重定向到 `/product/:id` | 已完成 |
| `*` | 404 | 已完成 |

## 演示商品与旧链接

- 首页「猜你喜欢」不足一屏时会补位演示商品；这 13 个商品已写入后端种子数据（`backend/SCA-Mail/sql/15_seed_demo_products.sql`），拥有真实商品详情、SKU 与库存。
- 演示商品卡片统一指向 `/product/:id`，点击后进入真实详情页，不再落到功能预告。
- 旧地址 `/feature/product-detail?id=105` 会 302 到 `/product/105`；缺少 `id` 时回到全部商品列表。
- 首页接口整体失败时仍降级到演示数据，但降级卡片同样可打开详情；此时详情请求若失败会显示明确错误态，而不是空白。

## 购物车说明

- 所有购物车接口都要求登录，用户身份由网关透传的 `@CurrentUserId` 决定，前端**不传 userId**。
- `POST /api/cart` 同 SKU 累加；单 SKU 上限 99，单用户最多 100 种 SKU。
- `PUT /api/cart` 修改数量或勾选，数量 `<= 0` 即删除；`PUT /api/cart/selection` 批量勾选，`skuIds` 为空表示全选 / 全不选。
- `DELETE /api/cart/{skuId}` 删除单品；`POST /api/cart/checkout` 返回结算预览。
- 每次写操作都以后端返回的 `CartView` 覆盖本地状态，页头角标与购物车页共享同一份 Pinia 状态；数量修改串行提交，避免快速点击导致回跳。
- 失效商品保留在列表并标记 `invalidReason`，不可勾选、不计入金额，由用户显式删除。
- `available = null` 表示库存未知，不按售罄处理；结算金额最终以订单服务计算为准。

> 结算页会重新调用 `POST /api/cart/checkout` 取价，避免沿用上一页的旧金额；下单成功后按 SKU 调用
> `POST /api/cart/purchased/remove` 清理已购商品，失败不影响订单成立。

### 订单与支付

- 下单幂等：进入结算页生成一次 `requestId`，重复点击 / 网络重试复用同一个值；
  服务端用 `userId + requestId` 唯一索引兜底，重放返回首次订单（`idempotentReplay=true`）。
- 收银台先 `POST /api/pay` 建支付单，再按渠道展示：
  - `channel=MOCK` 时显示「模拟支付成功」，调用 `POST /api/pay/mock/{paymentNo}/pay`；
  - 其他渠道展示「我已完成支付」，轮询 `GET /api/pay/{paymentNo}` 直到 `status=20`。
- 支付结果以支付单状态为准，成功后回查订单详情再展示；`CallbackResultVO.orderStatus` 可能为空，不能直接用它判断。
- 订单详情按状态给动作：待支付 → 去支付 / 取消；已发货 → 确认收货；其余只读。
- 地址使用 `region` 文本 + `detailAddress`，不由前端做三级级联；`fullAddress` 由服务端拼接。

## 认证说明

- 后端契约：`POST /api/auth/login`、`POST /api/auth/register`、`POST /api/auth/refresh`、`POST /api/auth/logout`、`GET /api/auth/me`。
- 本地令牌键独立于管理后台：`mall_buyer_access_token` / `mall_buyer_refresh_token`。
- accessToken 过期或返回 `20001` / `20002` 时自动刷新并重放原请求一次；`/auth/me` 也走同一请求层。
- 刷新令牌一次一换。前端通过「单标签单飞 + Web Locks / localStorage 租约跨标签互斥」避免并发刷新触发后端重放检测。
- 未登录访问 `/account` 会跳转 `/login?redirect=/account&reason=required`；登录或注册成功后回到原目标。
- 未登录加购会跳转 `/login?redirect=/product/{id}&reason=cart`，登录后回到原商品。
- 登录响应不含手机号等完整资料，进入账户中心后再补拉 `/auth/me`。
- 演示账号：`buyer01 / Buyer@123456`。

## 目录约定

- `src/api`：接口适配层。字段映射、令牌与刷新集中在 `client.js` / `auth.js` / `tokenStorage.js` / `cart.js`，换后端只改这一层。
- `src/components/common`：无业务语义的通用组件，可在任意页面复用。
- `src/components/auth`：登录注册共用布局与卡片，跨认证页复用。
- `src/components/layout`：页头、页脚、搜索、悬浮工具条。
- `src/components/product`：商品卡片、网格、SKU 选择器与数量步进器，首页、搜索、详情页共用。
- `src/components/cart`：购物车行与结算栏，只依赖 `cart` store 与通用组件；结算已改为跳转 `/checkout`。
- `src/components/address`：收货地址表单抽屉（新增 / 编辑共用）。
- `src/utils/order.js`：订单状态、Tab、支付倒计时与规格快照格式化。
- `src/components/home`：首页专属分区组件。
- `src/composables`：数据聚合、轮播状态机与认证表单校验，视图不直接取数。
- `src/stores/user.js`：用户态、登录态恢复与跨标签令牌同步；注销 / 失效时同步清空购物车。
- `src/stores/cart.js`：购物车聚合状态，写操作统一用后端 `CartView` 回填。
- `src/utils/spec.js`：`specJson` 解析、规格分组、售罄判断与 SKU 匹配。
- `docs/frontend-architecture.md`：完整组件划分、设计令牌、数据契约与分阶段规划。

## 接口说明

### 商品公开接口

- `GET /api/product/page?pageNo=&pageSize=&keyword=&status=1`
- `GET /api/product/category/list`
- `GET /api/product/{id}/detail`：返回商品与全部 SKU；`available = null` 表示库存未知，`status = 1` 为上架。

首页调用商品分页；真实数据不足一屏时使用平台精选补足，并在页面上以「部分为平台精选」标注，不冒充真实库存。

### 购物车接口（均需登录）

| 接口 | 说明 |
| :--- | :--- |
| `GET /api/cart` | 当前用户购物车，实时刷新价格 / 库存 / 上下架 |
| `POST /api/cart` | 加购，同 SKU 累加 |
| `PUT /api/cart` | 批量修改数量 / 勾选，数量 `<= 0` 删除 |
| `PUT /api/cart/selection` | 批量勾选，`skuIds` 为空表示全选 / 全不选 |
| `POST /api/cart/checkout` | 结算预览，返回勾选行、件数与应付金额 |
| `POST /api/cart/purchased/remove` | 下单成功后清理已购 SKU |
| `DELETE /api/cart/{skuId}` | 删除单品 |

错误码：`37001` 商品不存在、`37002` 数量非法、`37003` 种类超限、`37004` 商品不可购买、`37005` SKU 不可购买、`37006` 未勾选有效商品、`37007` 单件超过 99、`37008` 库存不足。前端在 `src/utils/errors.js` 统一转为买家可读文案。

### 地址 / 订单 / 支付接口（均需登录）

| 接口 | 说明 |
| :--- | :--- |
| `GET /api/user/address` | 收货地址列表（按最近更新倒序） |
| `POST /api/user/address` | 新增地址；首条自动默认，最多 20 条 |
| `PUT /api/user/address/{id}` | 修改地址 |
| `DELETE /api/user/address/{id}` | 删除地址；删默认地址会自动补位 |
| `PUT /api/user/address/{id}/default` | 设为默认（同用户互斥） |
| `POST /api/order` | 提交订单（`requestId` 幂等） |
| `GET /api/order/page` | 我的订单分页，返回 `{ order, items }` 行 |
| `GET /api/order/{orderNo}` | 订单详情（订单主体 + 明细 + 状态日志） |
| `POST /api/order/{orderNo}/cancel` | 取消待支付订单 |
| `POST /api/order/{orderNo}/confirm` | 确认收货（已发货 → 已完成） |
| `POST /api/pay` | 发起支付，返回 `paymentNo` 与渠道参数 |
| `GET /api/pay/{paymentNo}` | 支付单查询（用于轮询） |
| `POST /api/pay/mock/{paymentNo}/pay` | Mock 通道模拟支付成功 |

错误码：`31007~31009` 地址、`34001~34003` 订单、`35001/35004/35007/35008` 支付，
文案统一在 `src/utils/errors.js`。

### 认证接口

| 接口 | 说明 |
| :--- | :--- |
| `POST /api/auth/register` | 用户名、密码、手机号必填；昵称选填，最长 32 位 |
| `POST /api/auth/login` | 返回用户基础信息、accessToken 与 refreshToken |
| `POST /api/auth/refresh` | 刷新令牌一次一换，返回新的令牌对 |
| `GET /api/auth/me` | 返回脱敏手机号、角色、状态与上次登录时间 |
| `POST /api/auth/logout` | 全端下线并作废刷新令牌 |

## 已知边界

- 失效商品不会自动删除，需用户显式删除；这是后端既定行为，页面已提供原因提示。
- 注册会写入开发库；本仓库不会提交真实手机号或用户数据。
- 忘记密码、用户协议与隐私政策目前为功能预告页。
- 后端种子数据中的 `img.example.com` 为占位域名，前端映射到本地图片，避免无效请求。
- 图片素材来自 Unsplash，仅作演示，不用于商业分发。

## 下一阶段

第四阶段已完成地址簿、下单、订单列表/详情与 Mock 支付闭环。真实支付渠道（微信 / 支付宝）与退款入口仍待接入；当前 Mock 通道只在演示 / 联调环境开放。