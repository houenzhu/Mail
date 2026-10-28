# SCA-Mail 商场微服务

基于 Spring Cloud Alibaba + JDK 25 的 B2C 商场微服务平台骨架，需求见 [商场微服务项目需求文档（V1.0）.md](./商场微服务项目需求文档（V1.0）.md)。

> 需求文档当前为 V1.13，共 30 章。常用入口：用例与业务规则看第十六章、领域边界与 ADR 看第十七章、接口契约与错误码看第十八章、缓存与消息看第十九章、一致性与失败场景看第二十章、安全与 Sa-Token 迁移看第二十一章、测试与验收看第二十三～二十四章、第三阶段落地记录看第二十六～二十九章；**当前实现状态、已知接口/权限缺口与客服 Agent 规划见第三十章**。

## 技术栈

| 组件 | 版本 |
| :--- | :--- |
| JDK | 25 |
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.3 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| Nacos | 3.2.4（注册中心 + 配置中心） |
| MySQL | 8.0.32（本机 `127.0.0.1:3306`） |
| Redis | 8.8.0（本机 `192.168.198.133:6379`，**本项目固定 db1**；约束见需求文档 19.7） |
| RocketMQ | 5.5.1（本机 `127.0.0.1:9876`；**客户端与 broker 版本对齐**，SCA BOM 的 5.3.1 已在 `mall-dependencies` 覆盖） |
| 构建 | Maven 3.9+ |

版本统一在 `mall-dependencies` 中维护，业务模块不要直接写版本号。

## 模块与端口

| 模块 | 端口 | 说明 |
| :--- | :--- | :--- |
| `mall-gateway` | 10000 | 统一入口网关（WebFlux + lb 路由 + **Sa-Token 会话鉴权** + 身份头透传；对外契约仍是 `Authorization: Bearer`） |
| `mall-auth` | 8101 | 认证授权中心：注册 / 登录 / 刷新 / 注销 / 当前用户；凭证落 `mall_auth`，**令牌与账号会话以 Redis db1 的 `satoken:*` 为准**（访问令牌 300s、刷新令牌 7d，见需求文档 21.9） |
| `mall-user` | 8102 | 用户服务：用户档案（`mall_user`）、内部建档接口、当前用户档案查询 |
| `mall-product` | 8103 | 商品服务：商品/SKU 查询、分页、新增/编辑/上下架/逻辑删除，分类品牌查询与维护；商品详情走 Redis 缓存 |
| `mall-inventory` | 8104 | 库存服务：查询 / 初始化 / 扣减 / 回滚（条件更新防超卖） |
| `mall-order` | 8105 | 订单服务：下单（Redis 幂等标记 + DB 唯一索引）/ 状态机 / 取消 / 支付回调 / 超时兜底 |
| `mall-pay` | 8106 | 支付服务：支付单 / 渠道回调验签 / 主动查询兜底 / 全额退款 / 支付对账（**已落地**：支付单 / 回调验签 / 主动查询兜底 / 全额退款 / 支付对账 + 3 个定时任务，见需求文档第二十八章 28.10~28.13） |
| `mall-promotion` | 8107 | 营销服务骨架；优惠券、秒杀等业务尚未实现 |
| `mall-admin` | 8111 | **后台 BFF**：聚合各服务的后台数据能力，对外统一 `/api/admin/**`（已落地商品分页/详情/新增/编辑/上下架/逻辑删除及分类品牌 CRUD；不连业务库） |
| `mall-common` | - | 公共模块：统一响应 `Result`、`ResultCode`、异常处理、缓存 / 分布式锁 / 幂等组件、内部凭据校验 |
| `mall-api` | - | 服务间调用接口定义（Feign 接口 + DTO） |
| `mall-dependencies` | - | 统一依赖版本管理 BOM |

## 当前实现边界

- 管理后台已接通登录、商品管理与分类/品牌维护；库存页展示商品库存汇总，但库存调整和流水未实现。后台订单/退款管理与数据看板也未实现，见 [后台前端说明](../../front/vue-admin-web/README.md)。
- 买家端 `front/vue-mall` 仍为初始脚手架，尚无商城业务流程。
- 当前没有客服会话、工单、知识库或 LangChain Agent 服务。第 30 章记录了作为后续项目的范围、数据准备和接口安全要求；它是规划，不是已交付功能。
- **权限待修复**：`/api/admin/**` 网关路由要求登录，但后台商品读取 BFF 及其下游读取方法未统一要求 staff 角色；此外 `/api/product/admin/**` 被更宽的 `/api/product/**` 路由覆盖，不能依靠路径命名实现“仅 BFF 可访问”。详情和建议修复见需求文档 30.2。此状态不应标记为生产就绪。

网关内置路由（`spring.cloud.gateway.server.webflux.routes`）：

| 路径 | 目标服务 |
| :--- | :--- |
| `/api/auth/**` | `lb://mall-auth` |
| `/api/user/**` | `lb://mall-user` |
| `/api/product/**` | `lb://mall-product` |
| `/api/inventory/**` | `lb://mall-inventory` |
| `/api/order/**` | `lb://mall-order` |
| `/api/pay/**` | `lb://mall-pay` |
| `/api/promotion/**` | `lb://mall-promotion` |
| `/api/admin/**` | `lb://mall-admin`（后台管理端唯一入口，**不在鉴权白名单**，未登录一律 401） |

## Nacos 配置约定

每个服务在 `application.yml` 中通过环境变量配置。敏感项（数据库 / Redis 密码、内部令牌、支付密钥）不设仓库内默认值；首次运行前按 [`.env.example`](./.env.example) 准备值，并将其导入启动进程环境（Spring Boot 不会自动读取 `.env` 文件）。

| 配置 | 默认值 | 环境变量 |
| :--- | :--- | :--- |
| `spring.cloud.nacos.server-addr` | `127.0.0.1:8848` | `NACOS_SERVER_ADDR` |
| 注册/配置 namespace | 空（public） | `NACOS_NAMESPACE` |
| 注册/配置 group | `DEFAULT_GROUP` | `NACOS_GROUP` |
| `sa-token.token-name` / `token-prefix` / `timeout` / `is-concurrent` | 见各服务 `application.yml` | **网关与 mall-auth 必须逐字一致**（`token-name` 同时是 Redis key 命名空间，不要改，见需求文档 21.9.1） |
| `mall.auth.internal-token` | 空（必须配置） | `INTERNAL_TOKEN`（网关与全部服务一致，否则服务端视为匿名） |
| `mall.auth.phone-salt` | `mall-dev-phone-salt` | `PHONE_SALT`（需与 `sql/07_seed_user_auth.sql` 的盐一致） |
| `spring.data.redis.*` | `192.168.198.133:6379`，db1，2s 超时；密码空（按需配置） | `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` |
| `mall.cache.*`（mall-product） | TTL 1800s + 抖动 0~300s、空值 60s、锁 10s | `MALL_CACHE_ENABLED` 等（见需求文档 19.2 / 22.9） |
| `mall.auth.enabled`（mall-gateway） | 网关鉴权开关，默认 `true`（关闭时仅透传 traceId，仅限本地调试） | `AUTH_ENABLED`；**Redis 不可用时鉴权 fail-closed**（V1.10 起已无黑名单开关，见需求文档 21.9.4 / 27.6） |
| `mall.auth.cors.allowed-origin-patterns`（mall-gateway） | 跨域来源，默认 `*`（**生产必须收敛为具体域名**） | `CORS_ALLOWED_ORIGIN_PATTERNS` / `CORS_ALLOW_CREDENTIALS`；预检由 `CorsWebFilter`（最高优先级）短路，白名单另放行 `OPTIONS:/**`，见 21.9.5 |
| `mall.auth.internal-token`（mall-admin） | 同上游；BFF 需把该令牌透传给下游，否则下游视为匿名直连 | `INTERNAL_TOKEN` |
| `mall.idem.*`（mall-order） | 窗口 24h、占位 60s | `ORDER_IDEM_TTL_HOURS` / `ORDER_IDEM_PENDING_TTL_SECONDS` |
| `mall.pay.channel.*`（mall-pay） | 渠道码 `MOCK`、验签密钥空（必须配置）、本机渠道回调地址 | `PAY_CHANNEL_CODE` / `PAY_CHANNEL_SECRET` / `PAY_CHANNEL_NOTIFY_URL`（生产必须走环境变量，见需求文档 28.13.4） |
| `mall.pay.callback.tolerance-seconds`（mall-pay） | 300s 回调时间窗 | `PAY_CALLBACK_TOLERANCE_SECONDS`（见需求文档 28.6） |
| `mall.pay.query.*` / `mall.pay.close-expired.*` / `mall.pay.reconcile.*`（mall-pay） | 查询兜底 1 分钟、超时关闭 1 分钟、对账每日 02:00（三个任务默认全开） | `PAY_QUERY_ENABLED` / `PAY_CLOSE_EXPIRED_ENABLED` / `PAY_RECONCILE_ENABLED` 等（见需求文档 28.8 / 28.13.4） |
| `mall.mq.enabled` / `name-server`（order / pay / inventory） | `true` / `127.0.0.1:9876` | `MALL_MQ_ENABLED` / `ROCKETMQ_NAME_SERVER`（见需求文档第 29 章） |
| `mall.mq.producer-enabled` | `true`（order / pay）；**`false`（inventory，只消费不生产）** | 无（按服务写死，见 29.2 的按需装配口径） |
| `mall.mq.outbox.*`（order / pay） | 补发周期 30s、首次延迟 15s、批量 200、最多重试 3 次（退避 1s/5s/30s） | `MALL_MQ_OUTBOX_INTERVAL_MS` 等（见需求文档 20.3 / 29.4） |
| `mall.mq.consumer.enabled` | `true`（order：消费 `TOPIC_PAY_SUCCESS`；inventory：消费 `TOPIC_STOCK_ROLLBACK`）；`false`（pay） | `MALL_MQ_CONSUMER_ENABLED`（见需求文档 29.4） |

多环境隔离方式：在 Nacos 控制台创建 `dev` / `test` / `prod` 命名空间，启动时设置 `NACOS_NAMESPACE=dev` 即可。

## 数据库

按服务分库（database-per-service）：`mall_product` / `mall_inventory` / `mall_order` / `mall_user` / `mall_auth`/ `mall_pay`（`payment` / `payment_callback_log` / `refund` / `pay_reconcile_diff`，见需求文档 28.3），跨服务数据一律走接口，不做跨库 JOIN。**认证凭证（`mall_auth`）与用户档案（`mall_user`）分离**，以 `user_id` 对应，理由见需求文档 ADR-009。

消息表（`sql/09_mall_mq.sql`，见需求文档 29 章）：`local_message`（本地消息表 / Outbox，**落在生产方库** `mall_order` / `mall_pay`）与 `consumed_message`（消费幂等表，落在消费方库 `mall_inventory` / `mall_order` / `mall_pay`）。**刻意不建独立的 `mall_mq` 库**——Outbox 必须与业务写同库同事务，否则"业务已提交、消息未落库"的窗口会让 RPO=0 失效。

```powershell
# 建库建表 + 开发种子数据（幂等，可重复执行）
$env:MYSQL_PASSWORD = '你的密码'
powershell -ExecutionPolicy Bypass -File .\scripts\init-db.ps1
```

连接信息通过环境变量覆盖，`application.yml` 中只有本机默认值：`MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD`。

## 当前已落地接口

以下是当前代码中已经存在的主要接口，不等于所有业务用例都已完成。接口状态和未实现范围以需求文档第 18、25、30 章及各模块源码为准。

| 服务 | 方法 | 路径 | 说明 |
| :--- | :--- | :--- | :--- |
| auth | POST | `/api/auth/register` | 注册（用户名 / 手机号唯一 + 口令强度校验） |
| auth | POST | `/api/auth/login` | 登录，签发 accessToken（5min）+ refreshToken（7d） |
| auth | POST | `/api/auth/refresh` | 刷新令牌（一次一换，重放旧令牌会强制下线） |
| auth | POST | `/api/auth/logout` | 注销（**全端立即下线**：删除账号会话 + 撤销全部刷新令牌，所有设备的令牌立即失效，见 ADR-026） |
| auth | GET | `/api/auth/me` | 当前登录用户（手机号脱敏） |
| user | GET | `/api/user/profile` | 当前登录用户档案 |
| user | GET | `/api/user/{id}` | 按 ID 查档案（手机号脱敏） |
| user | POST | `/api/user/internal/create` | 内部建档（需内部令牌；网关拦截外部访问） |
| product | GET | `/api/product/{productId}` | 商品详情 |
| product | GET | `/api/product/{productId}/detail` | 商品详情 + SKU 列表 |
| product | GET | `/api/product/page` | 分页（categoryId / brandId / keyword / status） |
| product | GET | `/api/product/sku/{skuId}` | SKU 明细 |
| product | POST | `/api/product/admin` | 管理端新增商品（含 SKU 与库存初始化；`@RequireStaff(OPERATOR)`） |
| product | PUT | `/api/product/{productId}` | 管理端编辑商品（SKU 差量更新；`@RequireStaff(OPERATOR)`） |
| product | PUT | `/api/product/{productId}/status` | 管理端上下架（`@RequireStaff(OPERATOR)`） |
| product | DELETE | `/api/product/{productId}` | 管理端逻辑删除（`@RequireStaff(OPERATOR)`） |
| inventory | GET | `/api/inventory/{skuId}` | 可售库存 |
| inventory | POST | `/api/inventory/init` | 初始化库存 |
| inventory | POST | `/api/inventory/deduct` | 扣减（幂等键 `orderNo + skuId`） |
| inventory | POST | `/api/inventory/rollback` | 回滚（幂等键 `orderNo + skuId`） |
| order | POST | `/api/order` | 创建订单（幂等键 `userId + requestId`；`userId` 取自令牌，客户端传参无效） |
| order | GET | `/api/order/{orderNo}` | 订单详情（含明细与状态流转日志；校验归属，非本人且非 staff 返回 `20003`） |
| order | GET | `/api/order/page` | 我的订单分页（身份取自令牌；`status` / `pageNo` / `pageSize` 可选，**不接受 `userId` 参数**） |
| order | POST | `/api/order/{orderNo}/cancel` | 取消订单 |
| order | POST | `/api/order/internal/{orderNo}/pay-callback` | 支付回调（**仅 mall-pay 直连调用**：内部路径 + `X-Gateway-Token`，网关拦截外部访问；变更见需求文档 ADR-022 / 28.13） |
| order | GET | `/api/order/internal/{orderNo}/pay-view` | 订单支付视图（支付侧核对金额与状态；内部路径 + `X-Gateway-Token`，**须直连服务**） |
| order | POST | `/api/order/internal/{orderNo}/refund-callback` | 退款阶段回调（`REFUNDING` / `REFUNDED` / `REJECTED`；内部令牌） |
| pay | POST | `/api/pay` | 发起支付（幂等键 `userId + requestId`；校验订单归属、订单状态与金额） |
| pay | GET | `/api/pay/{paymentNo}` | 支付单查询（本人或 staff） |
| pay | POST | `/api/pay/refund` | 申请退款（限已支付未发货、全额；`refund.active_key` 唯一索引幂等） |
| pay | GET | `/api/pay/refund/{refundNo}` | 退款单查询 |
| pay | POST | `/api/pay/callback/{channel}` | 渠道回调（网关白名单；验签 + 时间窗 + 防重放，见需求文档 28.6） |
| pay | POST | `/api/pay/internal/sync/{paymentNo}` | 主动查询渠道并对齐（回调丢失兜底；内部令牌，**须直连服务**） |
| pay | POST | `/api/pay/internal/reconcile/{bizDate}` | 手工触发对账，返回差异清单（内部令牌，**须直连服务**） |
| admin | POST | `/api/admin/product/page` | **后台商品分页**（BFF 聚合 mall-product + mall-inventory：含库存、SKU 数、分类/品牌名；支持已删除态与按 ID 精确查） |
| admin | GET | `/api/admin/product/status-counts` | 后台商品列表的状态角标计数（全部 / 出售中 / 仓库中 / 已删除） |
| admin | GET | `/api/admin/product/sku?productId={id}` | 单商品 SKU 列表（编辑页回显） |
| admin | GET | `/api/admin/product/{id}` | 商品详情 + 真实 SKU 列表（编辑页回显） |
| admin | POST | `/api/admin/product` | 新增商品（下游 `@RequireStaff(OPERATOR)`） |
| admin | PUT | `/api/admin/product/{id}`、`/api/admin/product/{id}/status` | 编辑商品、上下架（下游 `@RequireStaff(OPERATOR)`） |
| admin | DELETE | `/api/admin/product/{id}` | 逻辑删除（下游 `@RequireStaff(OPERATOR)`） |
| admin | GET | `/api/admin/category/list`、`/api/admin/brand/list` | 分类/品牌字典列表 |
| admin | POST/PUT/DELETE | `/api/admin/category[/{id}]`、`/api/admin/brand[/{id}]` | 分类/品牌维护（写入由下游 `@RequireStaff(OPERATOR)` 校验） |
| product | GET | `/api/product/category/list`、`/api/product/brand/list` | 商品分类/品牌字典；当前作为 GET 商品路径也在网关匿名白名单内 |
| internal-intended | POST/GET | `/api/product/admin/page`、`/api/product/admin/status-counts`、`/api/product/admin/sku-batch`、`/api/product/admin/sku-by-ids` | 供 mall-admin 经 Feign 调用的商品管理读取接口；当前路径未被标记为 `/internal/`，授权缺口见下方 |
| inventory | POST | `/api/inventory/batch` | 批量查询库存，供 BFF 聚合商品页；当前需要登录但没有 staff 角色限制 |
| admin | POST | `/api/admin/inventory/adjust`、`/api/admin/inventory/record/page` | **尚未实现**：库存调整与流水分页，当前仅保留在计划/页面占位中 |

### 管理接口安全状态

网关 `/api/admin/**` 不在白名单内，未登录访问会返回 401；但这只代表**已登录**，不代表客服/运营角色授权。商品和分类/品牌写接口在 `mall-product` 已用 `@RequireStaff(OPERATOR)` 校验。当前待修复的读取授权边界包括：BFF 的商品分页、状态计数、SKU/详情读取没有 staff 校验；对应 `mall-product` 管理读取方法也没有 staff 注解；下游 GET 计数路径还受 `/api/product/**` 匿名白名单覆盖。路径命名和 Feign 使用约定不能代替授权，因此买家可能读取管理数据，修复前不应视为生产就绪。详情和修复建议见需求文档第 30.2。

**后台接口的角色鉴权**：`mall-common` 提供 `@RequireStaff` 注解 + `RequireStaffInterceptor`，
标在接口或控制器上即生效（401 未登录 / 403 无权限）。角色包含关系**收敛在 `CurrentUser.hasRole` 一处**：
`ADMIN ⊇ OPERATOR ⊇ CS`，避免"管理员反而不能管商品"。当前已应用于商品新增与上下架
（修复了此前"任何登录用户都能新增商品"的越权缺陷，见需求文档 21.3）。

订单状态：`10 待支付 → 20 已支付 → 30 已发货 → 40 已完成`；`10 → 50 已取消`；`20 → 60 退款中 → 70 已退款`；退款驳回 `60 → 20`。流转由 `OrderStateMachine` 集中校验，非法流转返回业务异常。

配置中心按 `optional:nacos:${spring.application.name}.yaml` 加载，即 dataId 为 `mall-user.yaml` 这类文件名；Nacos 中不存在该配置时服务照常启动。若要改成本地优先级更高或强制拉取，调整 `spring.config.import` 即可。

> Nacos 3.x 的控制台独立在 `http://127.0.0.1:8080`，首次访问需要初始化管理员账号；客户端注册/配置端口仍是 `8848`（gRPC `9848`）。

## 本地运行

```powershell
# 1. 构建（首次会下载依赖）
mvn install -DskipTests

# 2. 启动全部服务（后台运行，日志写入 logs\<name>-run.log；也可 -Services mall-auth,mall-order 只启动部分）
powershell -ExecutionPolicy Bypass -File .\scripts\start-all.ps1

# 3. 停止全部服务
powershell -ExecutionPolicy Bypass -File .\scripts\stop-all.ps1
```

也可以直接在 IDEA 中运行各模块的 `MallXxxApplication`。

## 验证

```powershell
# 查询 Nacos 中已注册的实例（免登录开放接口）
curl "http://127.0.0.1:8848/nacos/v3/client/ns/instance/list?serviceName=mall-user&groupName=DEFAULT_GROUP&namespaceId=public"

# 服务自身健康检查
curl "http://127.0.0.1:8102/actuator/health"

# 商品详情（白名单，无需令牌）
curl "http://127.0.0.1:10000/api/product/1"

# 登录拿令牌（种子账号：buyer01 / Buyer@123456、admin / Admin@123456、operator01 / Operator@123456）
# PowerShell 取令牌：
#   $login = curl.exe -s -X POST "http://127.0.0.1:10000/api/auth/login" -H "Content-Type: application/json" `
#     -d '{"username":"buyer01","password":"Buyer@123456"}' | ConvertFrom-Json
#   $at = $login.data.accessToken
curl -X POST "http://127.0.0.1:10000/api/auth/login" -H "Content-Type: application/json" `
  -d '{"username":"buyer01","password":"Buyer@123456"}'

# 需要登录的接口统一带 Authorization
curl "http://127.0.0.1:10000/api/auth/me" -H "Authorization: Bearer <accessToken>"
curl "http://127.0.0.1:10000/api/user/profile" -H "Authorization: Bearer <accessToken>"
curl "http://127.0.0.1:10000/api/inventory/1001" -H "Authorization: Bearer <accessToken>"

# 下单（身份取自令牌，请求体不再有 userId；幂等：同一 requestId 重复提交只产生一个订单）
curl -X POST "http://127.0.0.1:10000/api/order" `
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" `
  -d '{"requestId":"req-001","receiverName":"张三","receiverPhone":"13800000000","receiverAddress":"杭州市西湖区文一西路 1 号","items":[{"skuId":1001,"qty":2}]}'

# 订单详情（校验归属：非本人且非 staff 返回 20003）
curl "http://127.0.0.1:10000/api/order/{orderNo}" -H "Authorization: Bearer <accessToken>"

# 我的订单分页（忽略请求参数里的 userId）
curl "http://127.0.0.1:10000/api/order/page?status=10" -H "Authorization: Bearer <accessToken>"

# 取消订单（库存自动回滚）
curl -X POST "http://127.0.0.1:10000/api/order/{orderNo}/cancel" `
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" -d '{"reason":"不想要了"}'

# 支付回调（支付链路完整实现后由 mall-pay 内部调用；为避免"仅需登录即可把订单标记为已支付"，
# 该接口已迁移到 /api/order/internal/{orderNo}/pay-callback 并要求内部令牌（V1.11 实测：直连不带令牌 20003、经网关 403），见需求文档 ADR-022 / 28.11 #15）
curl -X POST "http://127.0.0.1:10000/api/order/internal/{orderNo}/pay-callback" `
  -H "Content-Type: application/json" -H "X-Gateway-Token: <INTERNAL_TOKEN>" `
  -d '{"paymentNo":"P1234567890","payType":1,"amount":1198.00}'
# 支付链路（V1.11 已落地；完整回归脚本：scripts\e2e-pay.ps1，18 条用例）
# 发起支付（同一 requestId 幂等；返回 paymentNo 与 channelParams.sign）
curl -X POST "http://127.0.0.1:10000/api/pay" `
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" `
  -d '{"orderNo":"<orderNo>","payType":1,"requestId":"req-pay-001"}'

# 查询支付单
curl "http://127.0.0.1:10000/api/pay/<paymentNo>" -H "Authorization: Bearer <accessToken>"

# 渠道回调（白名单；签名串与算法见需求文档 28.4，timestamp 超出 ±300s 直接拒绝）
curl -X POST "http://127.0.0.1:10000/api/pay/callback/MOCK" -H "Content-Type: application/json" `
  -d '{"paymentNo":"<paymentNo>","channelTradeNo":"MOCK_TRADE_001","amount":499.00,"status":"SUCCESS","payType":1,"timestamp":1786000000000,"sign":"<SIGN>"}'

# 申请退款（限已支付未发货、全额；重复申请幂等返回同一 refundNo）
curl -X POST "http://127.0.0.1:10000/api/pay/refund" `
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" `
  -d '{"orderNo":"<orderNo>","reason":"不想要了"}'

# 内部接口（必须直连服务并带内部令牌；经网关访问含 /internal/ 的路径一律 403）
curl -X POST "http://127.0.0.1:8106/api/pay/internal/sync/<paymentNo>" -H "X-Gateway-Token: <INTERNAL_TOKEN>"
curl -X POST "http://127.0.0.1:8106/api/pay/internal/reconcile/2026-09-15" -H "X-Gateway-Token: <INTERNAL_TOKEN>"
```

种子数据（`sql/04_seed_data.sql`）：SKU 1001 库存 100、1002 库存 50、1003 库存 200，1004 / 1005 库存为 0（用于验证售罄与回滚）。

种子账号（`sql/07_seed_user_auth.sql`）：`admin` / `Admin@123456`（ADMIN）、`buyer01` / `Buyer@123456`（USER）、`operator01` / `Operator@123456`（OPERATOR），对应档案同步在 `mall_user.user`。

## 开发注意

- 使用 Feign 按服务名调用（`lb://`）的模块必须显式引入 `spring-cloud-starter-loadbalancer`：网关自带，`mall-product` / `mall-order` 需要自己声明。
- MyBatis 映射文件放在各模块 `src/main/resources/mapper/`，由 `mybatis.mapper-locations` 通配加载。
- **要在服务里用 mall-common 的 Mapper（消息表），必须在启动类 `@MapperScan` 里同时列出本服务 Mapper 包与 `com.zhe.mall.common.mq`**：MyBatis 的自动扫描只扫启动类所在包；而在别处注册 `MapperScannerConfigurer` / `MapperFactoryBean` 会让自动扫描整体退避，导致业务 Mapper 一起消失（踩坑记录见需求文档 29.6 坑 1）。
- 订单超时时间与扫描周期可在 `application.yml` 的 `mall.order.*` 下调整（`pay-timeout-minutes`、`timeout-scan.enabled` / `interval-ms`）。注意**超时关单的延时消息固定用 RocketMQ delayLevel=16（30 分钟）**，改成非 30 分钟时由 `OrderTimeoutTask` 兜底纠正（见需求文档 29.7）。
- 本机 JDK 25 命令行启动 Tomcat / Netty 会因 AF_UNIX 回环被系统策略拦截而失败（`Unable to establish loopback connection`）：`scripts/start-all.ps1` 会自动附加 `--patch-module=java.base=<%TEMP%\pipatch\out>`（可用 `PIPATCH_DIR` 覆盖），也可用 IDEA 启动。
- **RocketMQ broker 的存储路径要在 `broker.conf` 里用正斜杠**（如 `storePathRootDir = E:/rocketmq/store`）：RocketMQ 用 `java.util.Properties` 读配置，反斜杠是转义符，写成 `E:\rocketmq\store` 会因 `\r` 被当成回车而启动失败（见需求文档 29.6 坑 3）。另外 C: 盘使用率超过 `diskMaxUsedSpaceRatio`（默认 90%）时 broker 会拒绝所有写入并返回 CODE 14，本机已把存储迁到 E: 盘。

## 后续阶段

已可复现的主链路：注册 / 登录（Sa-Token 会话令牌）→ 浏览商品 → 创建订单（幂等，身份取自令牌）→ 扣减库存（防超卖）→ 支付回调 → 状态流转；取消与超时取消同样可复现。

已交付：Redis 接入（商品详情缓存、分布式锁、下单幂等标记，见需求文档 27.8 的 23 条验证）；**mall-pay 已交付**（支付单 / 回调验签 / 主动查询兜底 / 全额退款 / 支付对账 + 3 个定时任务，E2E 18/18 见需求文档 28.11，脚本 `scripts/e2e-pay.ps1`）；**认证框架已迁移到 Sa-Token 1.46.0 并完成实测回归**（签发端 mall-auth + 校验端 mall-gateway；自研 JWT / 黑名单 / 刷新登记共 6 个文件删除，网关改用 SaReactorFilter 校验会话，注销与强制下线升级为"全端立即失效"）——改动清单、6 个实测踩坑、错误码映射与 23 条回归记录见需求文档 **21.9 / 27.10 / ADR-025 / ADR-026**；**RocketMQ 已接入**（统一事件信封 + 本地消息表 Outbox + 消费幂等 + 死信；`TOPIC_ORDER_CREATED` / `ORDER_TIMEOUT`(延时 30min) / `STOCK_ROLLBACK` / `PAY_SUCCESS` 已生产，`mall-inventory` 消费库存回滚、**`mall-order` 消费支付成功**；**修掉了"支付成功但订单回调失败无补偿"与"取消后库存回滚失败只留日志"两个缺口**，并实测"停掉订单服务后支付回调 → 重启后订单由事件自愈到已支付"）——POC、逐文件清单、4 条踩坑与 12 条实测见需求文档 **第二十九章**。

待推进：**第 8 项秒杀**（Redis Lua 预扣 + MQ 削峰 + Sentinel 限流）、Sentinel 限流降级、Seata POC、可观测性（网关 traceId、指标与告警）、单测与 CI、压测验收 —— 详见需求文档第二十五章与 26.10。Dubbo（订单→库存）与 ShardingSphere 分库分表待 Boot 4 兼容性 POC 后再定。
