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
```

## 已实现页面

| 路由 | 页面 | 状态 |
| :--- | :--- | :--- |
| `/` | 首页（分类导航、轮播、活动入口、服务保障、猜你喜欢、场景展示） | 已完成 |
| `/search?q=` | 搜索结果（真实接口、综合/销量排序、空态与错误态） | 已完成 |
| `/product/:id` | 商品详情（实时价格、规格联动、库存、数量、加购、看了又看） | 已完成 |
| `/cart` | 购物车（登录守卫、数量、勾选、全选、删除、结算预览） | 已完成 |
| `/login` | 登录（校验、错误提示、redirect、记住用户名） | 已完成 |
| `/register` | 注册（校验、昵称选填、密码强度、协议确认） | 已完成 |
| `/account` | 账户中心（登录守卫、身份头、快捷入口、资料） | 已完成 |
| `/feature/:feature` | 功能预告（订单、收藏等未上线入口） | 已完成 |
| `*` | 404 | 已完成 |

## 购物车说明

- 所有购物车接口都要求登录，用户身份由网关透传的 `@CurrentUserId` 决定，前端**不传 userId**。
- `POST /api/cart` 同 SKU 累加；单 SKU 上限 99，单用户最多 100 种 SKU。
- `PUT /api/cart` 修改数量或勾选，数量 `<= 0` 即删除；`PUT /api/cart/selection` 批量勾选，`skuIds` 为空表示全选 / 全不选。
- `DELETE /api/cart/{skuId}` 删除单品；`POST /api/cart/checkout` 返回结算预览。
- 每次写操作都以后端返回的 `CartView` 覆盖本地状态，页头角标与购物车页共享同一份 Pinia 状态；数量修改串行提交，避免快速点击导致回跳。
- 失效商品保留在列表并标记 `invalidReason`，不可勾选、不计入金额，由用户显式删除。
- `available = null` 表示库存未知，不按售罄处理；结算金额最终以订单服务计算为准。

> 订单、地址与在线支付尚未进入本阶段，结算预览只展示商品、件数与应付金额，不伪造下单成功。

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
- `src/components/cart`：购物车行、结算栏与结算预览抽屉，只依赖 `cart` store 与通用组件。
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

### 认证接口

| 接口 | 说明 |
| :--- | :--- |
| `POST /api/auth/register` | 用户名、密码、手机号必填；昵称选填，最长 32 位 |
| `POST /api/auth/login` | 返回用户基础信息、accessToken 与 refreshToken |
| `POST /api/auth/refresh` | 刷新令牌一次一换，返回新的令牌对 |
| `GET /api/auth/me` | 返回脱敏手机号、角色、状态与上次登录时间 |
| `POST /api/auth/logout` | 全端下线并作废刷新令牌 |

## 已知边界

- 下单、地址、在线支付与订单列表仍属于后续阶段；购物车结算只做预览。
- 失效商品不会自动删除，需用户显式删除；这是后端既定行为，页面已提供原因提示。
- 注册会写入开发库；本仓库不会提交真实手机号或用户数据。
- 忘记密码、用户协议与隐私政策目前为功能预告页。
- 后端种子数据中的 `img.example.com` 为占位域名，前端映射到本地图片，避免无效请求。
- 图片素材来自 Unsplash，仅作演示，不用于商业分发。

## 下一阶段

第四阶段：地址管理、下单、订单列表与支付回跳。当前购物车的 `checkout` 只做结算预览，下一阶段接入订单服务后，再调用 `POST /api/cart/purchased/remove` 清理已购商品。