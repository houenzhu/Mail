# 商场管理后台前端（`vue-admin-web`）

Vue 3.5 + Vite 8 + Vue Router 5 + Pinia 4 + Element Plus + axios。管理后台当前已接通认证、商品管理和分类/品牌维护；库存调整、流水、后台订单/退款与数据看板仍未闭环。

实施记录见 [管理后台计划](../../admin-frontend-plan.md)，后端契约以 [商场微服务需求文档](../../backend/SCA-Mail/商场微服务项目需求文档（V1.0）.md) 为准。

## 启动

```bash
npm install
npm run dev
```

Vite 默认监听 5174，开发代理把 `/api` 转发到 `http://127.0.0.1:10000`。请先按 [后端说明](../../backend/SCA-Mail/README.md) 启动网关及所需服务。若本机 npm 缓存目录无写权限，可使用 `npm install --cache .npm-cache`。

## 请求模式

当前 `.env.development` 设置 `VITE_USE_MOCK=false`，管理接口走真实网关。需要离线演示时可设为 `true`；mock 会在浏览器本地处理认证和部分商品演示请求，不会访问真实认证服务，也没有覆盖商品详情/编辑、分类品牌写操作和库存调整等全部场景。它的商品分页处理仍匹配旧 GET 契约，而当前前端 API 用 POST，因此离线商品列表不能作为当前可用流程。未覆盖的 mock 路径会明确返回错误。真实服务当前已提供商品 CRUD 和分类/品牌 CRUD，详情见后端 README。

两种模式共用 `Result{code,message,data}` 解包逻辑。切换模式后应重启 Vite，让环境变量生效。

## 页面和接口状态

| 页面 | 路由 | 当前状态 |
| :--- | :--- | :--- |
| 登录 | `/login` | 已接真实 `/api/auth/**`，含后台准入校验 |
| 工作台 | `/workbench` | 页面框架已完成，指标接口待建 |
| 我的商品 | `/product/list` | 已接真实分页、状态计数、上下架、逻辑删除；销量 30 日口径未接通 |
| 发布/编辑商品 | `/product/create`、`/product/edit/:id` | 已接真实新增、详情回显、SKU 差量编辑 |
| 分类与品牌 | `/category/list` | 已接真实列表及增删改 |
| 库存管理 | `/inventory/list` | 通过商品管理 BFF 显示库存汇总；库存调整未实现 |
| 订单、退款、库存流水、数据看板 | 对应列表路由 | 占位页；后台管理接口未实现 |

“已有单笔退款查询”不等于后台退款管理：目前没有退款分页、客服审核/处理等后台工作流。

## 已有约束

1. Access token 固定 300 秒，不滑动续期；过期后走 `/api/auth/refresh`。
2. Refresh token 一次一换，刷新必须 single-flight；并发刷新或重放旧 token 会导致会话被强制下线。
3. 请求层默认统一显示错误；调用方自行展示时要传 `{ skipErrorToast: true }`，避免重复提示。
4. `main.js` 中先引入 Element Plus 样式，再引入项目主题样式；后者需要覆盖 Element Plus 变量。
5. 前端路由和按钮权限只用于体验；服务端角色校验才是安全边界。**当前部分后台商品读取接口尚未完成 staff 角色校验，状态与风险见根目录 README 和需求文档第 30 章。**

## 验收脚本

```bash
npm run build
npm run dev                 # 另开终端
npm run verify:ui           # 无头浏览器渲染页面并截图
npm run verify:login        # 登录、会话恢复、无令牌跳转、买家拒绝
```

提交的脚本可验证 UI 与登录流程；它们不覆盖完整真实商品 CRUD、库存调整或订单/退款工作流。README 和需求文档中的历史通过记录按原日期保留，本次文档整理没有重跑后端 E2E。

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
