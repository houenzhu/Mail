# 商城平台工作区

这是一个由商场微服务后端、运营管理后台和买家端前端组成的工作区。本文记录当前代码状态；历史设计与逐阶段验收记录见各子项目文档。

## 当前状态（2026-10-28）

| 部分 | 当前实现 | 尚未覆盖 |
| :--- | :--- | :--- |
| 后端 | Spring Cloud Alibaba 微服务；注册/认证、商品与分类品牌、订单、库存基础操作、支付退款、Redis、RocketMQ；`mall-admin` 提供商品管理 BFF；管理读取已补 staff 双层授权 | 管理端库存调整与流水查询、客服订单/退款工作流、数据看板、营销业务 |
| 管理后台 | 登录与权限体验、商品列表/新增/编辑/上下架/逻辑删除、分类品牌 CRUD；库存页可查看商品汇总 | 库存调整和流水、管理端订单/退款、看板仍为占位或未接通 |
| 买家端 | Vue/Vite 脚手架 | 商城页面、API、购物车、结算及用户流程尚未实现 |
| AI 客服 | 尚无 Agent、知识库、会话/工单存储或模型接入 | 需求边界与起步方案见后端需求文档第 30 章 |

### 当前需要优先核对的事项

- `product.sales` 仍没有可靠的业务写入方；后台销量不能视为实时销售统计。
- 库存调整、库存流水分页、后台订单/退款工作流和 dashboard 尚未形成闭环。
- 商品管理数据接口位于 `mall-product` 的 `/api/product/admin/**`，由 BFF 和下游服务双层校验 staff；网关公开商品 GET 白名单已收敛为明确的 C 端路径，不能覆盖管理路径。内部身份仍依赖正确的 `X-Gateway-Token`，直连或伪造身份头会按匿名处理。

## 文档入口

- [后端模块、配置、接口与运行说明](backend/SCA-Mail/README.md)
- [项目需求、业务规则、接口契约与当前 AI 客服规划](backend/SCA-Mail/商场微服务项目需求文档（V1.0）.md)
- [数据库脚本说明](backend/SCA-Mail/sql/README.md)
- [管理后台使用与实现状态](front/vue-admin-web/README.md)
- [管理后台实施计划与当前进度](admin-frontend-plan.md)
- [商品维护与库存阶段计划](.trae/documents/m3-商品维护与库存-实施计划.md)
- [买家端前端状态](front/vue-mall/README.md)

## 版本管理

当前仓库基线版本为 `0.1.0`。版本号遵循语义化版本，发布时同步更新根目录的 `VERSION` 和 `CHANGELOG.md`，并创建 `vX.Y.Z` Git 标签；日常改动使用短生命周期的 `feature/*` 或 `fix/*` 分支，验证后合并到 `main`。细则见 [VERSIONING.md](VERSIONING.md)。

## 本地开发入口

- 后端：按 [后端 README](backend/SCA-Mail/README.md) 准备 MySQL、Redis、Nacos、RocketMQ 后运行 Maven 和服务脚本。
- 管理后台：在 `front/vue-admin-web` 执行 `npm install`、`npm run dev`；开发代理将 `/api` 转发到本机网关，当前 `.env.development` 使用真实接口（`VITE_USE_MOCK=false`）。
- 买家端：在 `front/vue-mall` 执行 `npm install`、`npm run dev`；目前只用于查看脚手架页面。

各文档中的 E2E 结果是其记录日期时的本机验证结果，不代表本次文档整理重新运行了整套服务验收。
