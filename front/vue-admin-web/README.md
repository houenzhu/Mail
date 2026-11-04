# 商场管理后台前端（`vue-admin-web`）

Vue 3.5 + Vite 8 + Vue Router 5 + Pinia 4 + Element Plus + axios。管理后台当前已接通认证、商品管理、分类/品牌维护、库存调整与流水查询，以及订单查询、订单发货和退款单查询；退款审核工作流与数据看板仍未闭环。

认证层已支持**多标签页令牌同步**：多个标签同时过期时只发一次 `/auth/refresh`，避免后端重放检测把全端踢下线。

实施记录见 [管理后台计划](../../admin-frontend-plan.md)，后端契约以 [商场微服务需求文档](../../backend/SCA-Mail/商场微服务项目需求文档（V1.0）.md) 为准。

## 启动

```bash
npm install
npm run dev
```

Vite 默认监听 5174，开发代理把 `/api` 转发到 `http://127.0.0.1:10000`。请先按 [后端说明](../../backend/SCA-Mail/README.md) 启动网关及所需服务。若本机 npm 缓存目录无写权限，可使用 `npm install --cache .npm-cache`。

## 请求模式

当前 `.env.development` 设置 `VITE_USE_MOCK=false`，管理接口走真实网关。需要离线演示时可设为 `true`；mock 会在浏览器本地处理认证和部分商品演示请求，不会访问真实认证服务，也没有覆盖所有真实场景。它的商品分页处理仍匹配旧 GET 契约，而当前前端 API 用 POST，因此离线商品列表不能作为当前可用流程。未覆盖的 mock 路径会明确返回错误。真实服务当前已提供商品 CRUD、分类品牌 CRUD、库存调整与流水查询，详情见后端 README。

两种模式共用 `Result{code,message,data}` 解包逻辑。切换模式后应重启 Vite，让环境变量生效。

## 页面和接口状态

| 页面 | 路由 | 当前状态 |
| :--- | :--- | :--- |
| 登录 | `/login` | 已接真实 `/api/auth/**`，含后台准入校验 |
| 工作台 | `/workbench` | 页面框架已完成，指标接口待建 |
| 我的商品 | `/product/list` | 已接真实分页、状态计数、上下架、逻辑删除；销量 30 日口径未接通 |
| 发布/编辑商品 | `/product/create`、`/product/edit/:id` | 已接真实新增、详情回显、SKU 差量编辑 |
| 分类与品牌 | `/category/list` | 已接真实列表及增删改 |
| 库存管理 | `/inventory/list` | 商品级库存汇总；选择 SKU 后可按增量调整并写流水 |
| 库存流水 | `/inventory/record` | 真实分页，支持 SKU ID / 流水类型筛选，并显示可信操作人 |
| 订单管理 | /order/list | 已接真实分页与详情，并在详情抽屉内提供发货（已支付订单、仅 OPERATOR/ADMIN）：填写物流公司与单号，重复提交同单号幂等 |
| 退款处理 | /refund/list | 已接真实分页与详情：状态 Tab、退款单号/订单号/买家/日期筛选、详情含发起方与渠道退款单号；不提供审核/驳回（第四阶段） |
| 数据看板 | /dashboard | 占位页；后台管理接口未实现 |

后台退款目前只做**查询与审计**：退款申请仍走 C 端 `POST /api/pay/refund`，
审核/驳回、部分退款和退货退款属第四阶段，因此页面上没有假按钮。

## 已有约束

1. Access token 固定 300 秒，不滑动续期；过期后走 `/api/auth/refresh`。
2. Refresh token 一次一换，刷新必须 single-flight；并发刷新或重放旧 token 会导致会话被强制下线。
   本标签内由请求层单飞，**跨标签由 `src/api/refresh-lock.js` 互斥**（Web Locks，退化 localStorage 租约锁）；
   抢到锁后先比对 `localStorage` 中的令牌，其他标签已刷新则直接复用，绝不再发第二次 refresh。
3. 其他标签刷新/登录/退出时，`storage` 事件会把最新令牌同步进本标签；令牌被清除则回到登录页
   （不整页 reload，保留当前筛选条件）。
4. 登录后的 `redirect` 只接受站内绝对路径（`/...`），站外 URL 与 `//host` 一律回落到工作台。
5. 请求层默认统一显示错误；调用方自行展示时要传 `{ skipErrorToast: true }`，避免重复提示。
6. `main.js` 中先引入 Element Plus 样式，再引入项目主题样式；后者需要覆盖 Element Plus 变量。
7. 前端路由和按钮权限只用于体验；服务端角色校验才是安全边界。库存读取/流水要求 staff，库存调整由 BFF 与 `mall-inventory` 两层限制为运营/管理员。

## 验收脚本

```bash
npm run build
npm run dev                 # 另开终端
npm run verify:ui           # 无头浏览器渲染页面并截图
npm run verify:login        # 登录、会话恢复、无令牌跳转、买家拒绝
npm run verify:multitab     # 多标签页令牌同步：并发 401 只刷新一次、退出后另一标签跟随
npm run verify:order        # 订单管理页：分页、详情抽屉、状态 Tab（M4 查询）
npm run verify:refund       # 退款列表/详情 + 订单发货弹窗（M4 第二切片）
```

`verify:refund` 的发货用例需要一笔「已支付」订单；本地可用
`node scripts/seed-paid-order.mjs` 通过真实 C 端链路（建单 → 支付 → 内部查单）造数据。
脚本不覆盖完整真实商品 CRUD、库存调整或退款审核流程；这些仍需使用真实登录账号手工验收。

## 目录

```text
src/
├── api/           # 请求封装、认证与商品/目录 API
├── assets/styles/ # 主题变量与全局样式
├── components/    # 后台布局与通用组件
├── composables/   # 表格、字典与权限辅助逻辑
├── constants/     # 菜单与状态字典
├── mock/          # 可选的部分离线演示数据与处理器
├── router/        # 页面路由与登录态守卫
├── stores/        # 用户与布局状态
└── views/         # 商品、分类、库存和占位页面
```

