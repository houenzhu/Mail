# 买家端前端（`vue-mall`）

好物集商城 C 端，基于 Vue 3 + Vite + Pinia + Vue Router。当前完成**阶段二：登录 / 注册 / 账户中心**，首页与搜索页继续接入真实商品接口，接口不可用时自动降级为本地演示数据。

## 快速开始

```bash
npm install
npm run dev         # http://127.0.0.1:5175，/api 代理到 127.0.0.1:10000
npm run build       # 生产构建
npm run verify      # 首页 / 搜索 UI 回归（需先启动 dev）
npm run verify:auth # 登录 / 注册 / 账户中心 UI 验收（需先启动 dev 与后端）
```

## 已实现页面

| 路由 | 页面 | 状态 |
| :--- | :--- | :--- |
| `/` | 首页（分类导航、轮播、活动入口、服务保障、猜你喜欢、场景展示） | 已完成 |
| `/search?q=` | 搜索结果（真实接口、综合/销量排序、空态与错误态） | 已完成 |
| `/login` | 登录（校验、错误提示、redirect、记住用户名） | 已完成 |
| `/register` | 注册（校验、昵称选填、密码强度、协议确认） | 已完成 |
| `/account` | 账户中心（登录守卫、身份头、快捷入口、资料） | 已完成 |
| `/feature/:feature` | 功能预告（购物车、订单等未上线入口） | 已完成 |
| `*` | 404 | 已完成 |

## 认证说明

- 后端契约：`POST /api/auth/login`、`POST /api/auth/register`、`POST /api/auth/refresh`、`POST /api/auth/logout`、`GET /api/auth/me`。
- 本地令牌键独立于管理后台：`mall_buyer_access_token` / `mall_buyer_refresh_token`。
- accessToken 过期或返回 `20001` / `20002` 时自动刷新并重放原请求一次；`/auth/me` 也走同一请求层。
- 刷新令牌一次一换。前端通过「单标签单飞 + Web Locks / localStorage 租约跨标签互斥」避免并发刷新触发后端重放检测。
- 未登录访问 `/account` 会跳转 `/login?redirect=/account&reason=required`；登录或注册成功后回到原目标。
- 登录响应不含手机号等完整资料，进入账户中心后再补拉 `/auth/me`。
- 演示账号：`buyer01 / Buyer@123456`。

## 目录约定

- `src/api`：接口适配层。字段映射、令牌与刷新集中在 `client.js` / `auth.js` / `tokenStorage.js`，换后端只改这一层。
- `src/components/common`：无业务语义的通用组件，可在任意页面复用。
- `src/components/auth`：登录注册共用布局与卡片，跨认证页复用。
- `src/components/layout`：页头、页脚、搜索、悬浮工具条。
- `src/components/product`：商品卡片与网格，首页和搜索页共用。
- `src/components/home`：首页专属分区组件。
- `src/composables`：数据聚合、轮播状态机与认证表单校验，视图不直接取数。
- `src/stores/user.js`：用户态、登录态恢复与跨标签令牌同步。
- `src/utils/authPreferences.js`：记住用户名等非敏感认证偏好。
- `docs/frontend-architecture.md`：完整组件划分、设计令牌、数据契约与分阶段规划。

## 接口说明

### 商品公开接口

- `GET /api/product/page?pageNo=&pageSize=&keyword=&status=1`
- `GET /api/product/category/list`
- `GET /api/product/{id}/detail`

首页调用商品分页；真实数据不足一屏时使用平台精选补足，并在页面上以「部分为平台精选」标注，不冒充真实库存。

### 认证接口

| 接口 | 说明 |
| :--- | :--- |
| `POST /api/auth/register` | 用户名、密码、手机号必填；昵称选填，最长 32 位 |
| `POST /api/auth/login` | 返回用户基础信息、accessToken 与 refreshToken |
| `POST /api/auth/refresh` | 刷新令牌一次一换，返回新的令牌对 |
| `GET /api/auth/me` | 返回脱敏手机号、角色、状态与上次登录时间 |
| `POST /api/auth/logout` | 全端下线并作废刷新令牌 |

## 已知边界

- 购物车、下单、商品详情等入口仍指向功能预告页，属于后续阶段。
- 注册会写入开发库；本仓库不会提交真实手机号或用户数据。
- 忘记密码、用户协议与隐私政策目前为功能预告页。
- 后端种子数据中的 `img.example.com` 为占位域名，前端映射到本地图片，避免无效请求。
- 图片素材来自 Unsplash，仅作演示，不用于商业分发。

## 下一阶段

第三阶段：商品详情、SKU 选择与购物车。购物车入口继续指向功能预告页，`ProductCard` 的 `detailPath` 在商品详情页落地后统一替换。
