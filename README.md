# 商城平台工作区

这是一个由商场微服务后端、运营管理后台和买家端前端组成的工作区。本文记录当前代码状态；历史设计与逐阶段验收记录见各子项目文档。

## 当前状态（2026-11-05）

后端当前编译与运行基线为 **JDK 21**。本机已有 JDK 21 时无需准备 JDK 25 的 `PipeImpl` 补丁；历史 JDK 25 兼容性记录保留在需求文档中。

| 部分 | 当前实现 | 尚未覆盖 |
| :--- | :--- | :--- |
| 后端 | Spring Cloud Alibaba 微服务；注册/认证、商品与分类品牌、**购物车**、订单、库存管理、支付退款、Redis、RocketMQ；`mall-admin` 提供管理 BFF 与看板聚合；管理读取已有 staff 双层授权 | 客服订单代客处理、营销业务、部分/退货退款 |
| 管理后台 | 登录与权限、商品管理、分类品牌 CRUD、库存调整与流水、订单管理（分页/详情/时间线/发货/**代客确认收货**）、退款查询与**审核/驳回**、多标签页令牌同步（0.4.0）、工作台与数据看板（0.5.0）、**订单确认收货闭环（0.6.0）**、**退款审核流（0.7.0）** | 商品图上传、部分退款 |
| 买家端 | Vue/Vite：首页与瀑布流、搜索页、登录 / 注册 / 账户中心；商品与认证接口已接通，商品接口支持降级 | 商品详情、购物车页面、结算、地址与订单管理（购物车**后端**已于 0.8.0 交付，UI 未接入） |
| AI 客服 | 尚无 Agent、知识库、会话/工单存储或模型接入 | 需求边界与起步方案见后端需求文档第 30 章 |

### 当前需要优先核对的事项

- `product.sales` 仍没有可靠的业务写入方；后台销量不能视为实时销售统计。
- 库存调整与流水分页已接入后台；后台订单查询（分页 + 详情，0.2.0）、订单发货与退款单查询（0.3.0）、
  数据看板（0.5.0）、确认收货（0.6.0）已接通；`orders` 的物流字段由 `sql/11_order_shipping.sql`、
  完成时间由 `sql/12_order_completed.sql` 补齐（均可重复执行）。
  退款审核/驳回已于 0.7.0 闭环：`audit=true` 落待审核，运营/管理员同意或驳回，客服只读。
- 看板口径：GMV 按 `paid_at`、订单量按 `created_at`，固定 `Asia/Shanghai`；售罄率分母为 0 时返回 `null`
  （前端显示 “—”），不把「没有库存记录」误报成 0%。趋势图用原生 SVG，未引入 ECharts。
- 令牌刷新已具备**跨标签互斥**（0.4.0）：多标签同时过期只发一次 `/auth/refresh`，
  避免后端重放检测把全端踢下线；同时修正了刷新响应解析错误（旧实现会让 401 自动续期恒失败）。
  多标签验收见 `npm run verify:multitab`。
- 购物车（0.8.0）：`mall-cart`（8109）已交付后端接口，Redis 只存数量 / 勾选 / 加购时间，读取时实时刷新价格、上下架与库存；`/api/cart/**` 必须登录。真实接口验收脚本为 `backend/SCA-Mail/scripts/e2e-cart.ps1`（11/11）。
- 商品管理数据接口位于 `mall-product` 的 `/api/product/admin/**`，由 BFF 和下游服务双层校验 staff；网关公开商品 GET 白名单已收敛为明确的 C 端路径，不能覆盖管理路径。内部身份仍依赖正确的 `X-Gateway-Token`，直连或伪造身份头会按匿名处理。

## 文档入口

- [后端模块、配置、接口与运行说明](backend/SCA-Mail/README.md)
- [项目需求、业务规则、接口契约与当前 AI 客服规划](backend/SCA-Mail/商场微服务项目需求文档（V1.0）.md)
- [数据库脚本说明](backend/SCA-Mail/sql/README.md)
- [管理后台使用与实现状态](front/vue-admin-web/README.md)
- [管理后台实施计划与当前进度](admin-frontend-plan.md)
- [商品维护与库存阶段计划](.trae/documents/m3-商品维护与库存-实施计划.md)
- [买家端前端状态与组件规范](front/vue-mall/README.md)、[前端架构文档](front/vue-mall/docs/frontend-architecture.md)

## 版本管理

当前仓库基线版本为 `0.8.0`。版本号遵循语义化版本，发布时同步更新根目录的 `VERSION` 和 `CHANGELOG.md`，并创建 `vX.Y.Z` Git 标签；日常改动使用短生命周期的 `feature/*` 或 `fix/*` 分支，验证后合并到 `main`。细则见 [VERSIONING.md](VERSIONING.md)。

## 本地开发入口

- 后端：按 [后端 README](backend/SCA-Mail/README.md) 准备 MySQL、Redis、Nacos、RocketMQ 后运行 Maven 和服务脚本。
- 管理后台：在 `front/vue-admin-web` 执行 `npm install`、`npm run dev`；开发代理将 `/api` 转发到本机网关，当前 `.env.development` 使用真实接口（`VITE_USE_MOCK=false`）。
- 买家端：在 `front/vue-mall` 执行 `npm install`、`npm run dev`（默认 `http://127.0.0.1:5175`，`/api` 代理到本机网关）；首页与登录 / 注册 / 账户中心已可用；购物车页面尚未接入，页面清单见该目录 README。

各文档中的 E2E 结果是其记录日期时的本机验证结果，不代表本次文档整理重新运行了整套服务验收。


