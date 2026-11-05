# 商场管理后台前端（`vue-admin-web`）

Vue 3.5 + Vite 8 + Vue Router 5 + Pinia 4 + Element Plus + axios。管理后台当前已接通认证、商品管理、分类/品牌维护、库存调整与流水查询、订单查询/发货/确认收货、退款查询与审核/驳回，以及工作台与数据看板。

看板趋势图用**原生 SVG** 绘制，不引入 ECharts：只有折线、网格与刻度，几十行计算即可，
省掉约 300KB 依赖；后续出现堆叠/缩放等复杂图表再评估引入。

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
| 工作台 | `/workbench` | 已接真实指标：SKU 总数 / 待发货 / 退款中 / 今日 GMV |
| 我的商品 | `/product/list` | 已接真实分页、状态计数、上下架、逻辑删除；累计销量与 30 日销量均为真实数据（0.10.0） |
| 发布/编辑商品 | `/product/create`、`/product/edit/:id` | 已接真实新增、详情回显、SKU 差量编辑；商品主图支持上传到阿里云 OSS（0.9.0） |
| 分类与品牌 | `/category/list` | 已接真实列表及增删改 |
| 库存管理 | `/inventory/list` | 商品级库存汇总；选择 SKU 后可按增量调整并写流水 |
| 库存流水 | `/inventory/record` | 真实分页，支持 SKU ID / 流水类型筛选，并显示可信操作人 |
| 订单管理 | /order/list | 已接真实分页与详情；详情抽屉提供发货（已支付，仅 OPERATOR/ADMIN，重复同单号幂等）与代客确认收货（已发货，二次确认后 30 → 40，终态不再显示按钮） |
| 退款处理 | /refund/list | 已接真实分页与详情：状态 Tab（含待审核）、筛选、发起方/审核人、渠道退款单号；待审核单可由运营/管理员「同意退款 / 驳回」，客服只读 |
| 数据看板 | /dashboard | 已接真实聚合接口：4 张指标卡、GMV 与订单量 SVG 趋势（7/30 天切换）、库存售罄率、待办跳转 |

商品图上传走 `POST /api/admin/upload`（`multipart/form-data`，字段 `file`）：仅 OPERATOR/ADMIN，
后端按文件头识别 JPG/PNG/WEBP/GIF，默认上限 5 MB。密钥只在后端进程环境读取
（`AccessKeyID` / `AccessKeySecret`），不会下发到浏览器；桶默认公共读并返回稳定 URL，
避免签名过期后商品主图失效。若改用私有桶，设 `OSS_PUBLIC_READ=false` 即返回临时签名 URL。
管理员验收：`npm run verify:upload`（需先启动 mall-admin 并配置 OSS）。
退款申请仍走 C 端 `POST /api/pay/refund`（带 `audit=true` 时进入待审核）。
审批按钮只在 `status = 0` 且当前账号是 OPERATOR/ADMIN 时出现；真正的边界在服务端，
前端隐藏只是体验。部分退款和退货退款仍未实现。

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
npm run verify:refund-audit # 退款审核/驳回：待审核 Tab、审批、CS 只读（0.7.0）
npm run verify:dashboard    # 数据看板：指标卡、趋势图、库存售罄率、待办跳转（M5）
npm run verify:confirm      # 代客确认收货：真实造单 → 发货 → 二次确认 → 已完成（UC-ORDER-06）
npm run verify:upload       # 商品图上传：匿名 401 / 买家 403 / 伪装图片 38003 / 真实 OSS 回读
```

`verify:refund` 的发货用例需要一笔「已支付」订单；本地可用
`node scripts/seed-paid-order.mjs` 通过真实 C 端链路（建单 → 支付 → 内部查单）造数据。
脚本不覆盖完整真实商品 CRUD 与库存调整；退款审核由 `verify:refund-audit` 覆盖，
但仍建议使用真实登录账号抽查一次审批与驳回。

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

