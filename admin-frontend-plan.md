# 商场后台管理前端实现计划（V1.1）

> 目标：为 `backend/SCA-Mail` 商场微服务建设一个**千牛（淘宝商家后台）风格**的运营管理后台前端。
> 本文档只覆盖**后台管理端**（运营 / 客服 / 管理员），不覆盖 C 端买家商城。
> 参考视觉基线：千牛「商品 → 我的商品」页（左侧一级/二级菜单 + 顶部搜索与通知 + 状态 Tab + 筛选栏 + 商品表格 + 分页）。

| 版本 | 日期 | 修订内容 |
| :--- | :--- | :--- |
| V1.0 | 2026-09-19 | 初版：现状盘点、能力缺口、技术选型、目录结构、页面与路由清单、M1~M6 里程碑、验收与风险、后端待办 |
| V1.1 | 2026-09-19 | 决策落定：确认①后台前端独立为 `front/vue-admin-web`；②新建 `mall-admin` BFF（8111）聚合 `/api/admin/**`；③UI 库用 Element Plus；④不上 TypeScript；⑤顺手修复商品写接口越权（`@RequireStaff`）。相应更新 3.4 / 4.1 / 5 / 10 / 13 与 12.2 |
| V1.2 | 2026-09-19 | 对齐实际工程命名：后台目录确认为 **`front/vue-admin-web`**（脚手架已建、`node_modules` 未装）；C 端定名 **`front/vue-mall`**（`vue-mail` 为旧名待重命名）。更新 1.1 / 1.2 / 4.1 / 五（新增 5.1 现状与改造动作）/ R-08 / 12.2 / 14 |
| V1.3 | 2026-09-19 | **前端界面已落地**：后台骨架 + 登录 + AdminLayout + 「我的商品」样板页 + 发布商品 / 分类品牌 / 库存管理 / 工作台，交付与实测见新增的 **十四章**（含 3 个实测缺陷与修复、无头 Chrome 验收脚本）。原「开工顺序」顺延为十五章 |
| V1.4 | 2026-09-19 | **后端配套已落地**：网关 CORS/OPTIONS（G-16）、`mall-admin` BFF 8111 + 网关路由（ADR-N1）、`@RequireStaff` 角色鉴权（G-11 + 修 G-01 越权）、管理端商品分页/角标/批量 SKU（G-02）、库存批量查询（G-06）、分类品牌字典（G-08）。新增 14.2 交付情况（含 14 条单测与单测抓出的角色包含缺陷）与 14.4 本机启动前提 |
| V1.5 | 2026-10-28 | 当前状态校准：商品新增/编辑/目录 CRUD 已落地；更新库存、订单/退款后台与 dashboard 缺口；说明 mock 已非默认、买家端仍为脚手架；记录商品读取接口 staff 授权缺口与 AI 客服前置事项 |

> **本文档与 `backend/SCA-Mail/商场微服务项目需求文档（V1.0）.md` 的关系**：那份文档是**后端 + 业务**的唯一事实来源（ADR、错误码、状态字典、接口契约）；本文档是**后台前端**的实施计划。两者冲突时，后端契约以需求文档为准，前端实现以本文档为准。
> 现有需求文档中与本计划直接相关的锚点：3.1（服务端口）、16.1（角色权限矩阵）、16.2（用例清单）、18.1~18.3（接口约定与错误码）、21.9.5（**CORS/OPTIONS 预警**）、26.1（身份链路）、ADR-013（`/api/admin/**` 才接受 `userId` 参数）。

> **当前状态索引（2026-10-28）**：当前代码快照见第 14.5 节；第 3、10、12、13 节及 14.1～14.4 保留 2026-09-19 的初期盘点/实施记录，不是当前待办清单。当前商品管理读取接口有 staff 授权缺口，修复建议见需求文档 30.2。客服 Agent 尚未实现，其产品边界与前置项见需求文档第 30 章。

---

## 一、现状盘点

### 1.1 已有资产

| 层 | 现状 |
| :--- | :--- |
| 后端 | `backend/SCA-Mail`：网关、认证、用户、商品、库存、订单、支付、营销骨架及 `mall-admin` BFF；第三阶段 Redis / 支付 / RocketMQ 已有按日期记录的落地验证 |
| 数据 | 按服务分库脚本、种子数据、Redis db1 与 RocketMQ Outbox/消费幂等表均已存在；具体表清单见后端 `sql/README.md` |
| 管理后台 | `front/vue-admin-web` 已有真实认证、商品新增/编辑/上下架/删除、分类品牌 CRUD；mock 可选且 `.env.development` 当前关闭 |
| 买家端 | `front/vue-mall` 仍为 create-vue 示例脚手架，无商城 API 或买家业务页面 |
| 管理后台未闭环 | 库存调整/流水、订单/退款管理、数据看板仍缺；商品管理读取接口的 staff 权限需修复，详见需求文档 30.1~30.2 |

### 1.2 结论

后台前端已从脚手架演进为部分可用的运营后台。后续应按领域闭环补齐缺失接口与页面；当前优先级不是重新创建后台，而是修复商品读取授权边界，并补库存管理和订单/退款工作流。初版范围盘点保留在下文作决策记录，当前状态见 14.5。

---

## 二、参考产品结构 → 我们的映射

千牛的导航结构是"业务域 → 二级功能"两级；我们按**已有后端能力**取子集，不硬凑。

| 千牛一级菜单 | 我们的菜单 | 落地形态 | 依赖后端 |
| :--- | :--- | :--- | :--- |
| 首页 | **工作台** | 核心指标卡 + 待办 + 快捷入口 | ⚠️ `GET /api/admin/dashboard` 待建，页面仅框架 |
| 商品 | **商品管理** | 商品列表/上下架/新增/编辑/逻辑删除，分类品牌维护 | ✅ BFF 与下游写接口已落地；商品读取的 staff 授权待修 |
| 交易 | **订单管理** | 管理端订单列表、详情、发货、退款处理 | ⚠️ 买家本人订单与支付/退款查询存在；后台全量查询/发货/退款工作流未实现 |
| — | **库存管理** | 库存汇总、调整、流水查询 | ⚠️ 商品页可显示库存汇总；分页库存、调整和流水查询未实现 |
| 营销 | **营销中心** | 优惠券、秒杀活动 | ⛔ `mall-promotion` 仅骨架，**本期不做**，菜单占位并置灰 |
| 客服 | **退款/售后** | 并入订单管理二级菜单 | ⚠️ 买家可申请全额退款；客服退款管理和 Agent 均未实现 |
| 数据 | **数据看板** | GMV / 订单量 / 售罄率趋势 | ⚠️ 待建（M5 可选项） |
| 资产 / 财务 / 金融 / 服务 | — | 不属于本项目业务范围 | 不做 |

### 2.1 截图元素 → 可复用性逐项核对

以千牛「我的商品」页为样板，逐元素对照我们的数据面：

| 截图元素 | 我们的数据来源 | 缺口 |
| :--- | :--- | :--- |
| 状态 Tab（全部3 / 出售中3 / 仓库中0 / 草稿2 / 回收站0 / 违规0） | `product.status`（0 下架 / 1 上架）+ `deleted` 逻辑删除 | 只有「在售 / 仓库中」两态；**草稿、回收站、违规无对应字段** → 本期 Tab 收敛为「全部 / 出售中 / 仓库中 / 已删除」 |
| 商品名称 + 商品 ID | `product.name` / `product.id` | ✅ 有 |
| 主图缩略图 | `product.main_image` | ✅ 有；空值使用占位展示 |
| 价格 | `product.price_min` / `price_max` | `AdminProductRow` 返回价格区间 |
| 库存 | `inventory.available`（`mall-inventory`，独立库） | ✅ BFF 批量聚合为商品页库存汇总；单独 SKU 库存管理页待做 |
| 累计销量 | `product.sales` | 字段返回，但缺可靠写入方，展示值不能视为真实销量 |
| 30 日销量 | — | ❌ 无。需从 `mall_order.order_item` 按 `created_at` 聚合（跨库，必须走接口） |
| 质量分（基础分/扶优分） | — | ❌ 无此概念。**建议不做**，替换为「库存健康度」等有数据支撑的列 |
| 创建时间 / 发布时间 | `product.created_at` / `updated_at` | 商品管理 BFF 已返回创建/更新时间 |
| 操作（编辑 / 删除 / 上下架） | `/api/admin/product/**` | 商品新增、编辑、逻辑删除及上下架已落地；读取授权仍需补齐 |
| 顶部公告条 / 搜索 / 通知 / 帮助 | 前端静态或后端公告接口 | 本期做静态壳 + 前端本地通知 |
| 左下角「生意管家」浮窗 | — | 不做 |

---

## 三、初版后端能力缺口盘点（2026-09-19 历史快照）

> 本节记录后台项目开工前的缺口，不是当前待办清单。其后多项已完成；当前状态看 14.5。当前授权缺口以需求文档 30.2 为准。

### 3.1 当时的管理端接口缺失状态

按需求文档 16.1 的既定约定：后台接口统一 `/api/admin/**` 前缀，由 `mall-admin` 聚合，按角色授权。**在 2026-09-19 初始盘点时**该前缀尚无接口；此后已完成部分实现，当前授权缺口见需求文档 30.2。

| 编号 | 缺口 | 说明 | 优先级 |
| :--- | :--- | :--- | :--- |
| G-01 | **商品写接口无角色校验** | `ProductController#create` / `#updateStatus` 目前**只要求登录**，任何 `buyer01` 都能新增商品、改上下架。这是既有的**越权缺陷**，与本计划无关也必须修 | **P0（安全）** |
| G-02 | 管理端商品列表 | 需要"全部商品（含下架/已删除）+ 销量 + 时间 + 库存"，现有 `GET /api/product/page` 是 C 端语义（`ProductDTO` 仅 7 个字段、无库存无时间） | **P0** |
| G-03 | 商品编辑 / 逻辑删除 | 无 `PUT /api/admin/product/{id}`、`DELETE /api/admin/product/{id}` | **P0** |
| G-04 | 管理端订单列表 | `GET /api/order/page` **强制按当前登录人过滤**（`@CurrentUserId`），运营看不到全量订单 | **P0** |
| G-05 | 订单发货 / 确认收货 | `POST /api/admin/order/{orderNo}/ship`（UC-ORDER-07，P1 用例）不存在；`/confirm`（UC-ORDER-06）也不存在 | **P0**（发货） |
| G-06 | 库存分页列表 | 只有 `GET /api/inventory/{skuId}` 单条，管理端需要分页 + 按 SKU 名称搜索 | **P0** |
| G-07 | 库存调整与流水查询 | `UC-ADMIN-01`（手工调库存并写流水）未实现；`inventory_record` 流水无查询接口 | **P0** |
| G-08 | 分类 / 品牌管理 | 商品新增必须传 `categoryId` / `brandId`，但没有任何接口能列出或维护它们 → **发布商品表单无法填** | **P0** |
| G-09 | 文件上传（商品图） | 无上传接口 / 对象存储。本期可先用**图片 URL 输入框**兜底，不做上传 | P1 |
| G-10 | 数据看板 | `GET /api/admin/dashboard`（UC-ADMIN-03）不存在 | P2 |
| G-11 | 角色鉴权机制 | `mall-common` 有 `UserRole`（USER/OPERATOR/CS/ADMIN）、`CurrentUser#hasRole` / `#isStaff`，但**没有任何注解或拦截器强制校验**；也没有 `@RequireStaff` 之类能力 | **P0** |

### 3.2 数据面缺口

| 编号 | 缺口 | 说明 | 优先级 |
| :--- | :--- | :--- | :--- |
| G-12 | `product.sales` 无写入方 | 建表有字段、种子值非 0，但下单成功后**没有任何代码回写**。若直接展示会造成"销量永远不变"的假象 | P1 |
| G-13 | 无 30 日销量口径 | 需跨库聚合 `mall_order.order_item`，只能由 `mall-admin` 或 `mall-order` 提供接口 | P2 |
| G-14 | 商品无审核/草稿态 | 截图中的"草稿/违规"态无字段支撑 | P2（本期不做） |
| G-15 | `mall_user.user_address` 未建表 | 影响收货地址管理，属第四阶段，后台本期不涉及 | P2 |

### 3.3 网关配置缺口

| 编号 | 缺口 | 说明 | 优先级 |
| :--- | :--- | :--- | :--- |
| G-16 | **网关无 CORS 配置，且 `OPTIONS` 预检会被鉴权拦成 401** | 需求文档 21.9.5 明确预警：`SaReactorFilter` 是 `WebFilter`，会拦住 `OPTIONS`。**这是前端第一次调 API 就会撞上的坑**，且报错是 401 而非 CORS，排查成本高。**必须在 M1 解决**：加 `spring.cloud.gateway.server.webflux.globalcors` 配置，并放行 `OPTIONS:/**` | **P0** |
| G-17 | 限流未实现 | 需求文档 21.5 只有阈值表。与本计划弱相关，本期不做 | P2 |

### 3.4 后端配套方案（ADR 草案）

| 决策 | 建议 | 理由 |
| :--- | :--- | :--- |
| **ADR-N1：是否新建 `mall-admin` BFF 服务** | ✅ **已决策（V1.1）：本期引入**（8111，聚合各服务） | ① 需求文档已规划该服务与 `/api/admin/**` 约定，迟早要做；② 管理端列表天然需要**跨服务聚合**（商品 + 库存 + 销量），让前端发 N 个请求拼装既慢又脆弱；③ 全量订单列表无法靠扩展现有 C 端接口优雅解决；④ 与 C 端接口解耦，避免为后台需求污染 C 端契约。**代价**：多一个服务与部署单元。**已否决替代方案**：在各业务服务内散加 `/api/admin/**`、不建新服务——聚合逻辑会散落前端，后续再抽 BFF 要重构 |
| **ADR-N2：角色鉴权怎么加** | ✅ **已决策（V1.1）：在 `mall-common` 加 `@RequireStaff` 注解 + 拦截器，并同步修复 G-01 越权** | 复用现有 `UserRole` / `CurrentUser`，改动小、覆盖面清晰；比散落 if 判断可靠（对齐 ADR-013 的"从接口签名上消除越权面"理念） |
| **ADR-N3：`product.sales` 回写时机** | 支付成功（订单 `10 → 20`）时由 MQ 事件异步累加 | 与现有 `TOPIC_PAY_SUCCESS` 链路一致，天然幂等（`consumed_message` 兜底） |
| **ADR-N4：金额与时间格式** | 后端一律返回 `DECIMAL` → JSON number/string，前端统一按 `¥#,##0.00` 与 `YYYY-MM-DD HH:mm:ss` 渲染 | 对齐需求文档 6.4 的本地化要求（`Asia/Shanghai`） |

---

## 四、前端技术选型

### 4.1 选型结论

| 维度 | 选择 | 理由 |
| :--- | :--- | :--- |
| 项目位置 | **在已建目录 `front/vue-admin-web/` 内开发，与 C 端 `front/vue-mall/` 并列，不合并** | 后台与 C 端目标用户、设计语言、发布节奏完全不同；两者各自独立构建与发布，互不影响 |
| 框架 | **Vue 3.5 + Vite 8 + Vue Router + Pinia** | 与现有 `front/vue-mall`、`front/vue-admin-web` 脚手架以及本机 Node v24.9 一致；脚手架已有可用配置可直接沿用 |
| 语言 | **JavaScript（先不上 TypeScript）** | 后端 `Result` 契约简单、页面以 CRUD 为主；上 TS 会显著增加初期的字段映射成本。若后续接口膨胀到 50+，再评估迁移 |
| UI 组件库 | **Element Plus**（推荐，见 4.2 对比） | 后台管理是组件库的舒适区：表格 / 分页 / 树形菜单 / 抽屉 / 表单校验开箱即用，自研成本极高 |
| HTTP | **axios**（封装统一实例 + 拦截器） | 生态成熟，拦截器能力满足令牌注入与 401 重放 |
| 图表 | **ECharts**（按需引入） | M5 数据看板用；M1~M4 不引入 |
| 样式 | Element Plus 变量定制 + CSS 变量 | 做出千牛那种"紧凑、信息密度高"的观感，必须覆盖组件库默认的大间距 |
| 状态管理 | Pinia：`user`（令牌/角色/权限）、`app`（菜单折叠/主题）、`dict`（状态字典缓存） | 对齐需求文档 16.5 的"状态字典"概念，前端集中一份 code→label 映射 |

### 4.2 UI 库对比

| 方案 | 优势 | 劣势 | 结论 |
| :--- | :--- | :--- | :--- |
| **Element Plus** | 后台组件最全、中文文档、Vue 3 原生、社区最大 | 默认视觉偏"通用后台"，需要定制才能像千牛 | ✅ **推荐** |
| Ant Design Vue | 表格能力更强、设计语言更接近淘宝系（蚂蚁系） | 包体更大，与 Vue 3 的迭代节奏略慢于 Element Plus | 备选 |
| Naive UI | 主题定制能力最强、TS 友好 | 生态与示例少于前两者，团队上手成本高 | 备选 |
| 自研组件 | 视觉 100% 可控 | 表格/树/表单校验全部自己写，工期至少翻 3 倍 | ❌ 不推荐 |

### 4.3 关键取舍说明

1. **必须先解决网关 CORS/OPTIONS（G-16），否则开发期无法直连 10000 网关**。开发期也可用 Vite `server.proxy` 把 `/api` 转发到 `http://127.0.0.1:10000` **规避 CORS**（同源），但**生产环境**跨域仍必须由网关解决，不能只靠代理。
2. **不做服务端渲染**，纯 SPA。
3. **不做国际化**，中文硬编码（对齐需求文档 6.4「文案中文」）。
4. **不做微前端**，单体 SPA；后台功能域之间用路由懒加载切分。

---

## 五、目录结构

### 5.1 现状与改造动作

`front/vue-admin-web/` **已由脚手架创建完成**，当前内容是 create-vue 默认模板：

| 类别 | 现有文件 | 处理 |
| :--- | :--- | :--- |
| 保留 | `index.html`、`package.json`、`vite.config.js`、`jsconfig.json`、`.gitignore`、`public/favicon.ico`、`.vscode/*` | 保留，按需改 `name` 与 `vite.config.js`（加代理） |
| 改写 | `src/main.js`、`src/App.vue`、`src/router/index.js`、`src/assets/*` | 改造成后台入口与全局样式 |
| **删除** | `src/components/HelloWorld.vue`、`TheWelcome.vue`、`WelcomeItem.vue`、`src/components/icons/*`（5 个）、`src/views/HomeView.vue`、`src/views/AboutView.vue`、`src/stores/counter.js` | 脚手架示例，与后台无关 |
| 新增 | `src/api/`、`src/components/layout/`、`src/components/common/`、`src/constants/`、`src/composables/`、`src/views/` 各业务页 | 见下方目标结构 |

> ⚠️ `node_modules` **尚未安装**（`front/vue-mall` 已装、`vue-admin-web` 未装），M1 第一步需先 `npm install`。
> ⚠️ 需新增依赖：`element-plus`、`axios`（+ M5 的 `echarts`）。脚手架现有依赖只有 `vue` / `vue-router` / `pinia`。

### 5.2 目标结构

```text
front/vue-admin-web/
├── index.html
├── package.json
├── jsconfig.json
├── vite.config.js               # @ 别名 + /api 代理到网关 10000
├── .env.development             # VITE_API_BASE_URL=/api
├── .env.production
└── src/
    ├── main.js                  # 挂载 Element Plus / Pinia / Router / 全局指令
    ├── App.vue
    ├── api/                     # 按后端域拆分，一域一文件
    │   ├── request.js           # axios 实例：注入令牌、统一解包 Result、401 静默刷新重放、错误提示
    │   ├── auth.js              # 登录 / 刷新 / 注销 / me
    │   ├── product.js           # 商品 CRUD、上下架、SKU
    │   ├── category.js          # 分类 / 品牌（依赖 G-08）
    │   ├── inventory.js         # 库存查询 / 调整 / 流水
    │   ├── order.js             # 订单列表 / 详情 / 发货 / 退款
    │   └── dashboard.js         # 看板（M5）
    ├── assets/styles/
    │   ├── variables.css        # 主题色、间距、圆角（对齐千牛的紧凑密度）
    │   └── index.css            # 全局重置 + Element Plus 覆盖
    ├── components/
    │   ├── layout/              # AdminLayout / SideMenu / TopBar / Breadcrumb / TagsView
    │   ├── common/             # PageContainer / SearchBar / DataTable / StatusTag / MoneyText / ImageThumb
    │   └── business/            # SkuEditor / ProductForm / OrderStatusSteps / StockAdjustDialog
    ├── composables/
    │   ├── useTable.js          # 分页 + 查询 + 加载态 的通用组合式封装
    │   ├── usePermission.js     # 基于 roles 的按钮级鉴权
    │   └── useDict.js           # 状态字典 → label/颜色
    ├── constants/
    │   ├── dict.js              # 对齐需求文档 16.5 状态字典（订单/支付/退款/库存流水/商品）
    │   └── menu.js              # 菜单树 → 路由映射
    ├── router/
    │   ├── index.js             # 路由实例 + 全局前置守卫（登录态、角色）
    │   └── routes.js            # 路由表（懒加载）
    ├── stores/
    │   ├── user.js              # accessToken / refreshToken / roles / profile
    │   └── app.js               # 菜单折叠、面包屑
    └── views/
        ├── login/LoginView.vue
        ├── dashboard/WorkbenchView.vue
        ├── product/ProductListView.vue      # ★ 对齐千牛截图
        ├── product/ProductEditView.vue
        ├── category/CategoryListView.vue
        ├── order/OrderListView.vue
        ├── order/OrderDetailView.vue
        ├── refund/RefundListView.vue
        ├── inventory/InventoryListView.vue
        ├── inventory/InventoryRecordView.vue
        └── error/NotFound.vue
```

---

## 六、鉴权、令牌与请求层设计

> 这是整个前端最容易出错、也最容易被低估的部分，单独成章。

### 6.1 登录时序

```text
┌────────┐        ┌──────────────┐        ┌───────────────┐
│ 浏览器 │        │ 网关 :10000  │        │ mall-auth 8101│
└───┬────┘        └──────┬───────┘        └───────┬───────┘
    │ POST /api/auth/login                        │
    │ {username,password}  ──────────────────────►│ 校验口令
    │                                             │ StpUtil.login(userId)
    │◄── Result{ accessToken, refreshToken,       │ 写 Redis db1 会话
    │      expiresIn:300, roles:["OPERATOR"] } ────┘
    │
    │ 存入 Pinia（内存） + localStorage（accessToken/refreshToken）
    │ 校验 roles ∩ {OPERATOR,CS,ADMIN} ≠ ∅，否则拒绝进入后台
    │
    │ 后续请求：Authorization: Bearer <accessToken>
```

**要点**：

- `LoginVO` 已含 `roles`，**无需额外请求**即可做后台准入判断（非 staff 直接提示"无后台权限"并清登录态）。
- 令牌有效期：**accessToken 固定 300s、不滑动续期**（需求文档 21.8.7 坑 4），refreshToken 7d。
- 存储：`accessToken`/`refreshToken` 放 `localStorage`（衡量过 XSS 风险；本项目无 SSR、无第三方脚本，可接受），其余用户信息放 Pinia 内存。

### 6.2 401 静默刷新与并发控制

```text
请求 → 401 (code=20001/20002)
   ├─ 是「刷新令牌本身」或重试过一次 → 清登录态 → 跳登录页（带 redirect）
   └─ 否则 → 进入单飞刷新队列
         ├─ 已有刷新在进行 → 排队等待其结果
         └─ 发起 POST /api/auth/refresh { refreshToken }
               ├─ 成功 → 更新令牌 → 重放原请求
               └─ 失败 → 清登录态 → 跳登录页
```

**必须实现的三个细节**（都是从后端语义反推出来的硬约束）：

| # | 约束 | 处理 |
| :--- | :--- | :--- |
| 1 | 刷新令牌**一次一换**，重放旧令牌会**强制全端下线** | 刷新必须**串行单飞**（`isRefreshing` 标志 + 等待队列），绝不能并发发两个 refresh |
| 2 | accessToken 仅 300s，后台页面会连续发多个请求 | 页面挂载时的并发请求极易同时撞 401 → 必须有**请求重放队列**，否则会出现"刷新成功但几个请求已被判失败" |
| 3 | 多标签页会各自持有令牌并各自刷新 → 触发重放踢人 | 用 `localStorage` 事件或 `BroadcastChannel` 同步令牌，或引入"刷新锁"避免多标签同时刷新。**M1 先做单标签可靠，多标签同步列为 M2 收尾项** |

### 6.3 统一响应解包

后端一律返回 `Result{code,message,data}`（HTTP 状态码与业务 `code` 分离：鉴权失败是 HTTP 401 + `code=20001/20002`，业务失败通常是 HTTP 200 + `code=3xxxx`）。因此 axios 响应拦截器**不能只看 HTTP 状态码**：

```js
// 伪代码
response => {
  const { code, message, data } = response.data
  if (code === 0) return data            // 直接把 data 交给调用方
  if (code === 20001 || code === 20002) return refreshAndReplay(response.config)
  ElMessage.error(message)               // 业务失败统一提示
  return Promise.reject(new BizError(code, message))
}
```

**错误码分支策略**（对齐需求文档 18.3）：`1xxxx` 表单字段级提示；`2xxxx` 登录态；`3xxxx` 业务提示（库存不足 / 状态不允许等）；`5xxxx`/`9xxxx` 通用错误提示 + 可重试。

### 6.4 路由守卫与按钮级权限

- **全局前置守卫**：无令牌 → `/login?redirect=...`；有令牌但 `roles` 不含 staff → 提示并清态。
- **菜单与路由**：按 `roles` 过滤（客服看不到"商品管理"的写操作）。
- **按钮级**：`usePermission()` 暴露 `hasRole('ADMIN')`，配合 `v-if`；导出 `v-perm` 指令供表格操作列使用。
- **注意**：前端权限只是**体验优化**，真正的边界在后端（G-01/G-11）。前端隐藏按钮**不等于**安全。

---

## 七、页面与路由清单

| 路由 | 页面 | 对应千牛 | 核心功能 | 依赖后端 | 当前前端状态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/login` | 登录 | 登录页 | 用户名口令登录、staff 准入校验 | ✅ 已有 | M1 |
| `/workbench` | 工作台 | 首页 | 指标卡、待办、快捷入口 | ⚠️ G-10 | 有工作台框架；指标未接通 |
| `/product/list` | **我的商品** | 商品→我的商品 | 状态 Tab、筛选、商品库存汇总、上下架、编辑、删除、分页 | ✅ 已接商品 BFF；销量口径未完善，读取授权待修 | **已交付（权限修复待办）** |
| `/product/create` | 发布商品 | 商品→发布商品 | SPU 表单 + SKU 多行编辑 + 库存初始化；分类/品牌下拉 | ✅ 商品新增和目录接口已接通 | **已交付** |
| `/product/edit/:id` | 编辑商品 | 商品→编辑 | 回显 + 修改 + SKU 增删改 | ✅ BFF 提供真实商品/SKU 回显和编辑 | **已交付** |
| `/category/list` | 分类与品牌 | 商品→商品分类 | 分类 CRUD、品牌 CRUD | ✅ 已接 BFF 和下游写接口 | **已交付** |
| `/order/list` | 订单管理 | 交易→订单管理 | 全量订单筛选、详情、发货 | ❌ 后台接口未实现 | 路由已注册；页面占位 |
| `/order/detail/:orderNo` | 订单详情 | 订单详情 | 商品明细、收货信息、状态时间线 | ❌ 后台详情接口未实现 | 尚未注册/实现，为目标路由 |
| `/refund/list` | 退款处理 | 客服→退款 | 退款单列表、状态筛选、详情 | ⚠️ 只有买家侧申请/单笔查询；后台列表与客服处理未实现 | M4 待做 |
| `/inventory/list` | 库存管理 | 商品→库存 | 商品级库存汇总、调整、低库存高亮 | ⚠️ 当前显示商品页库存汇总；SKU 库存分页/调整未实现 | M3 待做 |
| `/inventory/record` | 库存流水 | — | 按 SKU/订单号/类型筛选流水 | ❌ 后台流水接口未实现 | 路由已注册；页面占位 |
| `/dashboard` | 数据看板 | 数据 | GMV、订单量、售罄率趋势 | ❌ G-10 | 路由已注册；页面占位 |

> **M2 的 `我的商品` 页是整份计划的样板页**：它把「布局 / 表格 / 筛选 / 分页 / 状态字典 / 权限 / 请求层」全部串一遍。这一页做扎实，后续页面都是复制套路。

---

## 八、重点页面的实现要点

### 8.1 我的商品（`/product/list`）——对齐截图

```text
┌ 面包屑：商品管理 / 我的商品 ─────────────────────────────────────┐
├ 状态 Tab：全部(n) | 出售中(n) | 仓库中(n) | 已删除(n)   [发布商品]┤
├ 筛选栏：商品名称[____]｜商品ID[____]｜分类[▼]｜品牌[▼]｜状态[▼]   │
│         [搜索] [重置]                                            │
├ 表格：☐｜商品｜价格｜库存｜累计销量｜创建时间｜更新时间｜操作      │
│       ☐｜[图]名称  ID:xx｜¥11.50｜889376｜100｜...｜编辑 上下架 更多│
├ 分页：[共 n 条]  ‹ 1 2 3 ›  每页 10/20/50                       │
└──────────────────────────────────────────────────────────────────┘
```

**关键实现点**：

1. **状态 Tab 数量**：需要"各状态计数"。若后端分页接口不返回计数，则退化为**不带数字的 Tab**（M2 首版），后端补 `countByStatus` 后再加数字，避免前端发 4 个 count 请求。
2. **筛选条件与 URL 同步**：把查询条件写进 `query`（`useTable` 统一处理），保证刷新页面/分享链接可复现。
3. **表格行高与密度**：千牛的观感来自**紧凑**。Element Plus 默认 `size="large"` 间距过大，需全局设 `size="default"` 并把表格 `cell padding` 压到 8px 左右。
4. **商品图**：`main_image` 为 NULL 时用占位块（灰底 + 商品名首字），避免破图。
5. **操作列**：`编辑 | 上架/下架 | 更多▾`；"更多"里放"删除（二次确认）+ 复制商品ID"。
6. **批量操作**：截图有「批量下架 / 更多批量操作」，本期**先不做**（需后端批量接口），列 P1。

### 8.2 库存列的取数方式（重要）

商品在 `mall_product`，库存在 `mall_inventory`（**database-per-service，禁止跨库 JOIN**）。三种方案：

| 方案 | 做法 | 评价 |
| :--- | :--- | :--- |
| A. 管理端接口一次返回 | `GET /api/admin/product/page` 在服务端聚合库存（Feign 批量查 `mall-inventory`） | ✅ **推荐**：前端一个请求拿到完整表格，N+1 问题在服务端用批量接口解决 |
| B. 前端并发查询 | 列表返回后前端对每行的 SKU 逐个查库存 | ❌ N 个请求，页面卡顿，库存接口压力大 |
| C. `product` 表冗余库存 | 把可售库存冗余进 `product` | ❌ 引入双写一致性问题，与现有防超卖设计冲突 |

**结论：走 A**，需要 `mall-inventory` 提供**批量按 skuId 列表查询**的接口（当前只有单条 `GET /api/inventory/{skuId}`）。这是 G-06 的具体形态。

### 8.3 销量列的真实性

`product.sales` **当前没有任何写入方**（G-12）。若直接展示，运营会看到永远不变的数字。两个选择：

- **M2 先展示**并在文档/页面上标注"累计销量（历史数据）"，同时排期做 G-12 回写；
- 或用"已支付订单数"实时聚合替代（成本更高）。

**建议**：M2 照常展示 + 同里程碑内完成 G-12（支付成功 MQ 事件异步累加 `sales`），避免上线一个会撒谎的列。

### 8.4 发布商品（`/product/create`）

- SPU 字段：名称、副标题、分类、品牌、主图 URL、SKU 多行（编码/名称/价格/划线价/库存）。
- **必须先有分类/品牌数据（G-08）**，否则表单无法提交 → G-08 是 M3 的硬前置。
- SKU 编码唯一（`product_sku.sku_code`），后端返回 `32004` 时定位到具体行提示。
- 创建时后端会**同步调 `mall-inventory` 初始化库存**，失败整单失败 → 前端需给出明确失败原因（不是笼统"系统异常"）。
- 数量与金额校验对齐 BR-01/BR-03：金额 `BigDecimal` 两位小数，单 SKU 数量 1~99。

### 8.5 订单管理（`/order/list`）

- **阻塞点**：`GET /api/order/page` 强制按登录人过滤（G-04）。运营要的是**全量订单 + 按买家筛选**，必须新增 `/api/admin/order/page`。
- 状态 Tab 对齐状态字典：待支付(10) / 已支付(20) / 已发货(30) / 已完成(40) / 已取消(50) / 退款中(60) / 已退款(70)。
- 发货操作：`20 → 30`，走 `POST /api/admin/order/{orderNo}/ship`（G-05），非法流转后端返回 `34002`。
- 状态字典集中放 `constants/dict.js`，**与需求文档 16.5 逐条对齐**，避免前端自造 label。

---

## 九、状态字典与格式化（对齐需求文档 16.5）

| 字典 | 取值 | 前端展示 |
| :--- | :--- | :--- |
| 商品状态 | 0 下架 / 1 上架 | 灰 tag「仓库中」/ 绿 tag「出售中」 |
| 订单状态 | 10/20/30/40/50/60/70 | 蓝/绿/青/灰/灰/橙/紫 tag + 详情页步骤条 |
| 支付单状态 | 见需求文档 16.5 | tag |
| 退款状态 | 见需求文档 16.5 | tag |
| 库存流水类型 | 预占 / 扣减 / 回滚 | tag |
| 支付方式 | 0 未支付 / 1 微信 / 2 支付宝 / 3 余额 | 文本 |

**统一格式化（`utils/format.js`）**：金额 `¥#,##0.00`；时间 `YYYY-MM-DD HH:mm:ss`（**服务端时间为准**，对齐 BR-20）；数量千分位。

---

## 十、实施里程碑

> 每个里程碑都有可验证的交付物，避免"写了很久看不到东西"。

### M1 底座与登录（打通链路）

> **状态：已完成前端和后端基础配套。** 下方勾选项保留为初始实施清单；当前还需补做的是读取接口 staff 授权回归（见需求文档 30.2）。

- [x] 在 `front/vue-admin-web` 建立后台 UI、安装 Element Plus/axios 并移除脚手架示例页面
- [x] 改造入口/路由，配置 `@` 别名与 Vite 开发代理 `/api → http://127.0.0.1:10000`
- [x] 网关 CORS + 放行 `OPTIONS:/**`（G-16）
- [x] 创建 `mall-admin` 模块（8111）+ Gateway `/api/admin/**` 路由
- [x] `mall-common` 增加 `@RequireStaff`，并用于商品/目录写接口（G-11 / G-01）
- [x] `api/request.js`：Result 解包、令牌注入、401 静默刷新 + 请求重放
- [x] 登录页、路由守卫与后台准入校验
- [x] `AdminLayout`、业务菜单和 404 页面
- **验收**：用 `operator01 / Operator@123456` 登录后进入空壳工作台，令牌过期（5 分钟）后无感刷新，刷新后仍在当前页；`buyer01` 调用商品写接口被后端拒绝（`20003`）。

### M2 商品管理（样板页）★

> **状态：商品列表主流程已完成。** BFF 分页、状态角标、SKU/库存聚合和商品写接口已落地；`product.sales` 与 30 日销量仍没有可靠口径。

- [x] **后端配套**：`POST /api/admin/product/page`（G-02，BFF 聚合商品与库存信息）
- [x] `我的商品` 列表页：状态 Tab、筛选栏、表格、分页、行操作
- [x] 上下架（含二次确认与失败提示）
- [ ] G-12（`product.sales` 回写）——避免上线一个永远不变的销量列
- [x] `useTable` / `useDict` / `usePermission` 三个 composable 沉淀
- **当前验收口径**：商品分页、库存汇总、状态角标与上下架已接 BFF；销量缺少可靠写入方，不能宣称与真实销售数据一致。管理读取 staff 授权仍待修复。

### M3 商品维护与库存

> **状态：商品维护部分已完成，库存管理未完成。** 商品新增/详情回显/SKU 差量编辑、分类品牌 CRUD 已接通；库存调整和流水查询仍待后端接口。

- [x] **后端配套**：商品编辑/逻辑删除（G-03）、分类品牌 CRUD（G-08）和库存批量查询（G-06）
- [ ] **剩余后端**：SKU 库存分页、库存调整与流水查询（G-07）
- [x] 发布商品 / 编辑商品（SKU 多行编辑）
- [x] 分类与品牌管理
- [ ] 库存调整弹窗及流水页真实接通（当前库存页只展示商品级聚合数）
- **验收**：完整走通「建分类 → 发布商品（含 2 个 SKU + 初始库存）→ 列表可见 → 改价 → 调整库存 → 流水可查」。

### M4 订单与退款

> **状态：未开始后台闭环。** 买家侧订单、支付和退款服务存在，但管理端全量订单、发货、退款列表与客服处理接口尚未实现。

- [ ] **后端配套**：`GET /api/admin/order/page`（G-04，全量 + 筛选）、发货（G-05）
- [ ] 订单列表（状态 Tab、订单号/买家/时间筛选）
- [ ] 订单详情（明细、收货信息、金额、**状态流转时间线**）
- [ ] 发货操作
- [ ] 退款列表与详情
- [ ] 库存流水查询页
- [ ] （收尾）多标签页令牌同步
- **验收**：C 端下单支付后，后台能看到该订单并发货，状态流转与 C 端一致。

### M5 工作台与看板（可选）

- [ ] **后端配套**：`GET /api/admin/dashboard`（G-10）
- [ ] 工作台指标卡 + 待办
- [ ] 看板图表（ECharts：GMV / 订单量 / 售罄率趋势）
- **验收**：指标与数据库实际数据一致（可用 SQL 抽查）。

### M6 打磨（可选）

- [ ] 商品图上传（G-09，需对象存储决策）
- [ ] 批量上下架
- [ ] 表格列显隐 / 密度切换 / 导出 CSV
- [ ] 骨架屏、空状态、操作二次确认统一化
- [ ] 权限矩阵细化到按钮级

---

## 十一、验收标准

| 维度 | 标准 |
| :--- | :--- |
| 功能 | M1~M4 的验收项全部通过；后台能完整管理商品、库存、订单、退款 |
| 安全 | 非 staff 角色**无法**进入后台，且**直接调接口也被拒**（后端校验，不依赖前端隐藏） |
| 令牌 | accessToken 过期后无感刷新；连续多请求不出现"部分请求失败"；刷新令牌不被重放（不触发强制下线） |
| 一致性 | 页面展示的库存/销量/订单状态与 DB 直查结果一致 |
| 契约 | 所有时间以服务端为准；金额两位小数；状态 label 与需求文档 16.5 完全一致 |
| 体验 | 列表首屏有加载态，操作有反馈（成功/失败提示），失败可定位原因（用后端 `message`） |
| 兼容 | Chrome / Edge 最新版可用（不做 IE 兼容） |

---

## 十二、风险与待确认项

### 12.1 风险

> 以下 R-01～R-08 来自原始实施计划；已发生变化的项目在风险文本中标出当前状态。新增授权缺口见 14.5 和需求文档 30.2。

| # | 风险 | 影响 | 缓解 |
| :--- | :--- | :--- | :--- |
| R-01 | 后端管理接口最初零覆盖；当前商品管理和目录接口已实现，库存/订单/退款/看板仍有缺口 | 中 | 按 14.5 的剩余接口状态推进；不要把 mock 当成真实接口验收 |
| R-02 | 网关 CORS/OPTIONS 初期缺失；当前已实现 | 低 | Gateway 配置已加入 CORS filter 和 OPTIONS 放行 |
| R-03 | 刷新令牌"一次一换"语义被前端误用（并发刷新 → 强制全端下线） | 高 | 6.2 的单飞队列是**硬要求**，M1 必须实现并专门测试 |
| R-04 | `product.sales` 无写入方，展示了假数据 | 中 | 同里程碑完成 G-12，或标注数据口径 |
| R-05 | 库存/销量需跨库聚合，方案选错会引入 N+1 | 中 | 8.2 定案走管理端聚合接口 |
| R-06 | 商品/目录写接口初期缺少角色限制；当前写接口已加 `@RequireStaff`，但商品管理读取仍有 staff 授权缺口 | 高（安全） | 修复 BFF 与下游读取授权、内部路由外部访问并跑匿名/USER/CS/OPERATOR/ADMIN 集成用例，见需求文档 30.2 |
| R-07 | 状态/枚举前后端各写一份导致漂移 | 中 | `constants/dict.js` 与需求文档 16.5 对齐，并加注释指向文档章节 |
| R-08 | 工程名混乱（工作区叫 `Mail`、业务是商场） | 低 | 后台为 `vue-admin-web`，买家端目录已定为 `vue-mall`；旧名 `vue-mail` 的改名事项已完成 |

### 12.2 待确认项（开工前需拍板）

> **状态：Q1~Q4 已于 2026-09-19 确认并执行**；Q5 看板范围仍未定，Q6 默认不做 i18n。当前不是开工前待决策清单。

| # | 问题 | 我的建议 | 已确认结论 | 影响面 |
| :--- | :--- | :--- | :--- | :--- |
| Q1 | 后台前端是**独立项目**还是放进 C 端工程？ | 独立工程 | ✅ **独立 `front/vue-admin-web`**（与 C 端 `front/vue-mall` 并列，不合并） | 决定工程结构，影响后续所有目录 |
| Q2 | 是否**现在就建 `mall-admin` BFF（8111）**，还是先在各服务散加 `/api/admin/**`？ | 建 BFF（ADR-N1） | ✅ **新建 `mall-admin` BFF，端口 8111**，管理端接口统一由它聚合 | 决定后端工作量与前端聚合方式；M2/M4 的列表接口写在 BFF 内 |
| Q3 | UI 库用 **Element Plus** 还是 Ant Design Vue？ | Element Plus | ✅ **Element Plus** | 影响全部页面写法与视觉定制成本 |
| Q4 | 是否上 **TypeScript**？ | 先不上，纯 JS | ✅ **先不上 TypeScript，纯 JS** | 影响前期开发速度与后期可维护性 |
| Q4b | 是否顺手修掉商品写接口的越权缺陷（G-01）？ | 修 | ✅ **修**：`mall-common` 加 `@RequireStaff` 并应用到商品写接口（与 G-11 同一件事） | 决定 M2 是否包含安全修复 |
| Q5 | M5 工作台/看板是否本期做？ | 可延后到 M1~M4 之后 | ⏳ 待 M4 收尾时确认 | 决定是否需要后端做指标聚合 |
| Q6 | 通用状态是否需要 i18n？ | 不需要（中文） | ⏳ 默认不做 | 低 |

**决策连带影响（已完成的实施记录）**：

1. **Q2 建 BFF** ⇒ 已新增 `mall-admin` 到 Maven 聚合和 `Path=/api/admin/**` Gateway 路由；BFF 不单独建业务库。
2. **Q1 独立工程** ⇒ M1 直接在**已存在的** `front/vue-admin-web` 脚手架内改造（删示例组件 → 装 `element-plus`/`axios` → 加代理），C 端 `vue-mall` 本期不动。
3. **Q4b 角色鉴权** ⇒ 该能力是 M2 所有管理端接口的前置，**建议提到 M1 与 BFF 骨架一起做**，否则 BFF 一创建就是无鉴权状态。

---

## 十三、后端配套接口清单（原始计划与当前状态）

下表保留原接口设计以追踪需求，并标注当前状态。当前不存在的接口不得从“规划路径”推断其已可调用。

| 范围 | 当前接口/能力 | 当前状态 |
| :--- | :--- | :--- |
| BFF 与网关 | `mall-admin` 8111；Gateway `/api/admin/**` 路由与 CORS/OPTIONS | ✅ 已实现 |
| 角色鉴权 | `mall-common @RequireStaff`；商品及目录写接口要求 `OPERATOR` | ✅ 写接口已保护；⚠️ 商品读取 BFF/下游角色授权待补，见需求文档 30.2 |
| 商品分页/角标 | `POST /api/admin/product/page`、`GET /api/admin/product/status-counts` | ✅ 已实现；聚合库存和 SKU 数；销量字段没有可靠更新源 |
| 商品详情与维护 | `GET /api/admin/product/{id}`、`POST /api/admin/product`、`PUT /api/admin/product/{id}`、`PUT /api/admin/product/{id}/status`、`DELETE /api/admin/product/{id}` | ✅ 已实现，写权限在下游校验 |
| 分类/品牌 | `GET /api/admin/category/list`、`GET /api/admin/brand/list` 及对应 POST/PUT/DELETE | ✅ 已实现 |
| 库存分页/调整/流水 | 原规划 `/api/admin/inventory/page`、`adjust`、`record/page` | ❌ 尚未实现；目前有 BFF 商品列表中的库存汇总和 `POST /api/inventory/batch` |
| 管理端订单/发货 | `/api/admin/order/page`、详情、`ship` | ❌ 尚未实现；现有订单接口主要是买家本人订单与内部回调 |
| 后台退款工作流 | `/api/admin/refund/page` 等 | ❌ 尚未实现；已有买家退款申请与单笔查询，不含客服处理 |
| 数据看板 | `GET /api/admin/dashboard` | ❌ 尚未实现 |
| 商品图上传 | 原规划 `POST /api/admin/upload` | ❌ 尚未实现；当前使用图片 URL |
| 商品销量 | `product.sales` 累计写入、30 日销量 | ❌ 无可靠累计销量写入；30 日销量接口/口径也未完成 |

客服 Agent、客服会话/工单和知识库不在上述已实现范围；规划见需求文档 30.3~30.5。

---

## 十四、交付与进展记录

### 14.1 前端交付进展（2026-09-19，历史记录）

| 里程碑 | 状态 | 交付物 / 证据 |
| :--- | :--- | :--- |
| **M1 底座与登录** | ✅ 前端部分完成 | `front/vue-admin-web` 骨架改造完成：入口/路由/主题/请求层/登录页/AdminLayout/404。**后端配套（网关 CORS、`mall-admin`、`@RequireStaff`）未做**，故 M1 整体未闭环 |
| **M2 商品管理（样板页）** | ✅ 前端部分完成 | 「我的商品」列表页：状态 Tab（带计数）/ 筛选栏 / 表格 / 分页 / 上下架 / 批量上下架 / 删除；`useTable`·`useDict`·`usePermission` 三个 composable 已沉淀。**`GET /api/admin/product/page` 未实现，当前走 mock** |
| M3 / M4 / M5 | 🚧 部分占位 | 发布商品、分类品牌、库存管理已完成页面（只读或走 mock）；订单/退款/库存流水/看板为占位页并标注所缺接口 |

**验证方式（不是"能构建"就算过）**：用无头 Chrome 通过 CDP 真实渲染并断言，脚本随工程提交：

```bash
cd front/vue-admin-web
npm run dev            # 另开终端
npm run verify:ui      # 渲染各页面 + 截图到 verify-out/
npm run verify:login   # 真实走登录/会话恢复/无令牌重定向/买家被拒
```

`verify:login` 实测 8/8 通过；`npm run build` 通过（1689 modules，各页面按路由分块懒加载）。

**实测发现并修复的 3 个真缺陷**（只跑构建是发现不了的，记录备查）：

| # | 现象 | 根因 | 修复 |
| :--- | :--- | :--- | :--- |
| 1 | 注入令牌后访问 `/product/list` 是**整页空白** | mock 令牌被设计成 `mock-access-token-operator01-<时间戳>`，从令牌字符串按 `-` 切分取用户名，结果解析出 `"token"` → `/auth/me` 判无效 → 守卫跳登录但路由未变 → 空白 | 改为**用映射表**记录「令牌 → 用户名」。不透明令牌按定义就不可解析（真实 Sa-Token 是 UUID），用映射表才与真实语义一致 |
| 2 | 自定义橙色主题不生效，主色仍是 Element Plus 默认蓝 `#409eff` | `element-plus/dist/index.css` 同样声明 `:root{--el-color-primary:#409eff}`；两者同为 `:root`，**靠后的声明胜出**，而我把自己的样式引在了组件库之前 | 调整 `main.js` 引入顺序：先组件库、后自有主题变量。**该顺序已成硬约束，写进 README** |
| 3 | 买家账号登录时错误提示**弹两次** | 请求层按默认行为统一 toast 了业务失败，登录页 catch 里又按后端 message 弹了一次 | 登录接口传 `skipErrorToast`，文案由页面统一呈现；并把"调用方自己展示文案时必须传 `skipErrorToast`"写进 `request.js` 的约定与 README |

> ⚠️ 另外两条环境注意：① `npm install` 在本机沙箱下会因 `D:\NodeJs\node_cache` 不可写报 `EPERM`，加 `--cache .npm-cache` 即可；② dev server 监听 **IPv6 回环 `::1`**，须用 `http://localhost:5174` 访问，`127.0.0.1` 连不上。

### 14.2 后端配套交付情况（2026-09-19，本轮完成，历史记录）

M1 的三个阻塞项 + M2 的商品读取接口已全部落地（构建通过，单测 14/14）：

| 缺口 | 状态 | 落地内容 |
| :--- | :--- | :--- |
| **G-16 网关 CORS/OPTIONS** | ✅ 已修 | 新增 `GatewayCorsConfig`（`CorsWebFilter` 以 `@Order(HIGHEST_PRECEDENCE)` **先于** `SaReactorFilter` 短路预检），白名单补 `OPTIONS:/**` 作第二道保障；来源域与凭据可用 `CORS_ALLOWED_ORIGIN_PATTERNS` / `CORS_ALLOW_CREDENTIALS` 配置 |
| **ADR-N1 `mall-admin` BFF** | ✅ 已建 | 新模块 `mall-admin`（8111，纯聚合**不连库**）：`AdminProductController`（`POST /api/admin/product/page`、`GET /api/admin/product/status-counts`）+ `AdminFeignConfig`（把 `X-Gateway-Token` / `X-Trace-Id` / 身份头透传给下游，否则下游会把 BFF 当匿名直连） |
| **G-01 商品写接口越权** | ✅ 已修 | `ProductController#create` / `#updateStatus` 加 `@RequireStaff(UserRole.OPERATOR)` |
| **G-11 角色鉴权能力** | ✅ 已建 | `mall-common` 新增 `@RequireStaff` 注解 + `RequireStaffInterceptor`（401 未登录 / 403 越权，HTTP 状态码语义正确，不依赖 `@RestControllerAdvice`），在 `MallCommonAutoConfiguration` 全局注册、只对标注接口生效 |
| **G-02 管理端商品分页** | ✅ 已建 | `mall-product` 新增 `/admin/page`（支持**已删除态**与按 ID 精确查）、`/admin/status-counts`（一次返回角标）、`/admin/sku-batch`（按商品批量取 SKU）；`ProductDTO` 扩了 `categoryId/brandId/sales/createdAt/updatedAt` |
| **G-06 库存批量查询** | ✅ 已建 | `mall-inventory` 新增 `POST /api/inventory/batch`（`InventoryMapper.selectByIds`），BFF 侧整页只发 1 次 SKU + 1 次库存调用 |
| **G-08 分类/品牌字典** | ✅ 已建（读） | `mall-product` 此前**只有建表、没有 Mapper**——补齐 `Category`/`Brand` 实体 + Mapper + `/category/list`、`/brand/list`，发布商品的分类下拉不再无数据（增删改仍属 M3） |

**实测与验证**：

| 手段 | 结果 |
| :--- | :--- |
| `mvn install`（全 13 模块） | ✅ BUILD SUCCESS |
| `RequireStaffInterceptorTest`（9 条） | ✅ 全绿：该放行的放行（运营/管理员）、该拒的拒（买家 403、客服访问运营接口 403、匿名 401、类级注解、非 HandlerMethod 放行） |
| `AdminProductServiceTest`（5 条） | ✅ 全绿：跨服务字段合并（库存求和/SKU 数/分类品牌名）、**批量调用只发一次**（防 N+1 回归）、库存不可用降级、商品分页失败上抛、空页不发起聚合 |
| 新写 SQL 直接在 MySQL 上执行 | ✅ 列名与语法正确（`countGroupByStatus` 返回 status=1 共 3 条），与实际种子数据核对一致 |

**单测额外抓出的一个真缺陷**：ADMIN 访问 `@RequireStaff(OPERATOR)` 被拒。原 `CurrentUser.hasRole` 是严格相等匹配，而角色是账号级单一标签（种子 `admin` 只有 ADMIN），会导致"最高权限反而不能管商品"。已把包含关系收敛到 `CurrentUser.hasRole` 一处：**ADMIN ⊇ OPERATOR ⊇ CS**。

### 14.3 当时尚未完成的事项（2026-09-19，历史记录）

1. **未做真机端到端验证**：本机 JDK 25 需要 `--patch-module=java.base=<pipatch>` 才能让 Tomcat/Netty 绑端口（需求文档 12.8），而该系统临时目录已被清理、patch 源文件丢失，服务起不来 → 本轮改用**单测 + 直连 MySQL 验 SQL** 覆盖，未跑网关到 BFF 的真机链路。恢复方式见 14.4。
2. `GET /api/admin/product/page` 的 **30 日销量（G-13）** 仍为 null：需 `mall-order` 提供按时间窗聚合订单明细的接口，接口就绪后在 BFF 内补齐（**刻意不用累计销量冒充**，避免错误口径）。
3. `mall-product` 的关键字搜索是 `name/subtitle LIKE '%kw%'`，无法命中索引；数据量到 10 万 SKU 时需改用全文或搜索引擎（与 6.1 的性能目标相关）。
4. 分类/品牌的**增删改**（M3）、商品编辑与逻辑删除接口（G-03）、库存调整与流水（G-07）、订单管理端接口（G-04/G-05）仍待做。
5. 前端切真机：把 `front/vue-admin-web/.env.development` 的 `VITE_USE_MOCK` 置为 `false` 即可（页面代码无需改动）。该切换已完成，当前文件值为 `false`。

### 14.4 本机启动前提（2026-09-19 环境记录）

当时记录到 JDK 25 命令行启动 Tomcat/Netty 需要本机 `java.base` patch，否则可能因 AF_UNIX 回环权限而绑不上端口（见需求文档 12.8）。这是平台/机器相关的历史排障记录，不应假定当前开发环境仍然相同；启动失败时先按当前 JDK、启动脚本和错误日志复核：

```powershell
# 期望存在：$env:TEMP\pipatch\out\sun\nio\ch\PipeImpl.class（可用 PIPATCH_DIR 覆盖）
Test-Path (Join-Path $env:TEMP 'pipatch\out\sun\nio\ch\PipeImpl.class')
```

该 patch 仅用于当时的本机环境，**刻意不进仓库**。旧记录称其临时目录当时已清理；此处不再把它描述为当前环境状态。

### 14.5 当前状态快照（2026-10-28）

| 项目 | 当前状态 |
| :--- | :--- |
| 后台真实请求模式 | `front/vue-admin-web/.env.development` 为 `VITE_USE_MOCK=false`；Vite 代理指向网关 `127.0.0.1:10000` |
| 商品管理 | 商品分页/角标/库存聚合、详情、创建、编辑、上下架、逻辑删除已存在；分类/品牌列表及 CRUD 已存在 |
| 库存管理 | 商品页可显示聚合库存；`/api/admin/inventory/adjust`、`/api/admin/inventory/record/page` 尚未实现，库存页的调整/流水按钮保持占位 |
| 订单与退款 | 后台订单/退款页面仍为占位；服务侧的买家订单、支付与全额退款接口不能替代后台工作流 |
| 工作台/看板 | 指标接口 `GET /api/admin/dashboard` 尚未实现 |
| 安全 | 商品写入由下游 `@RequireStaff(OPERATOR)` 校验；BFF/下游管理读取接口尚未统一 staff 校验，且 `/api/product/admin/**` 读取路径受宽泛商品路由/匿名 GET 白名单影响，需按需求文档 30.2 修复和回归 |
| 买家端与客服 Agent | `front/vue-mall` 仍为脚手架；没有客服会话、工单、知识库或 Agent 实现 |

本快照用于替代前文“零接口 / 默认 mock / 编辑未实现”等历史描述；历史验证记录仍按原日期保留。

---

## 十五、开工顺序（一句话版，历史记录）

1. **先修网关 CORS/OPTIONS**（G-16）——否则前端第一步就撞 401。
2. **建 `mall-admin` BFF 骨架（8111）+ 网关 `/api/admin/**` 路由 + `@RequireStaff` 鉴权**（Q2/Q4b 决策）——三者同一批做，避免 BFF 一创建就是无鉴权状态。
3. **在已建脚手架 `front/vue-admin-web` 内改造 + 请求层 + 登录**（M1）——目标是"能登录、能无感刷新"。
4. **并行推后端 M2 接口 + `product.sales` 回写**（G-02/G-12）——前端同步用 mock 做「我的商品」页。
5. 之后按 M3 → M4 依次推进，每个里程碑以"能演示的完整闭环"结束。

---

> **历史决策（V1.2）**：后台 = `front/vue-admin-web`，C 端 = `front/vue-mall`，采用 `mall-admin` BFF(8111)、Element Plus、纯 JS，并修复商品写接口越权。该决策已执行；`vue-mall` 不再是待重命名目录。
> `Q5`（M5 看板）尚未交付，仍取决于 dashboard 接口和订单/支付统计口径。
