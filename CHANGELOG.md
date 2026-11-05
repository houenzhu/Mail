# 更新日志

本项目使用语义化版本。每个正式基线或发布都在此记录；未发布改动先记入「未发布」。

## 未发布

暂无。

## [0.10.0] - 2026-11-05

- 销量口径闭环：G-12 累计销量回写 + G-13 后台 30 日销量。
  - 订单支付回调 CAS 成功、状态日志写入后，在**同一事务**内通过 Outbox 登记
    `TOPIC_ORDER_PAID`（幂等键 `orderNo`）；事件带 `paidAt` 与订单明细
    `SalesItem(productId, skuId, quantity)`，`paidAt` 与库内 `orders.paid_at` 取自同一变量。
  - `mall-product` 新增消费方 `order-paid-sales`：同商品多 SKU 先合并数量，再按 `product_id`
    累加 `product.sales`；认领表 `product_sales_record(order_no, product_id)` 唯一键保证
    重复投递不重复加；商品不存在/已删除时回滚认领并抛错，交给 MQ 重投；累加后清理详情缓存。
  - 新增 `POST /api/order/admin/product-sales`（`mall-order`）：按 `orders.paid_at` 归窗，
    状态限 `20/30/40/60`，天数收敛到 1~90（默认 30），时区固定 `Asia/Shanghai`。
  - 后台商品分页 `sales30d` 改为真实 30 日销量：整页 ID 一次批量查询，不做 N+1；
    订单服务不可用时该列留空并 WARN，不影响商品列表其余字段。
  - 口径边界：销量按「支付成功即计入」，退款暂不回减；30 日销量与累计销量分开展示。
- 数据库：新增 `sql/14_product_sales.sql`，建 `mall_product.product_sales_record` 与
  `mall_product.consumed_message`（消费幂等表）。
- 验证：`ProductSalesServiceTest` 4 项、`AdminProductServiceTest` 30 日批量/降级用例、
  `OrderMapperXmlTest` SQL 校验全部通过；目标模块 `mvn test` 全绿；
  前端 `npm run build` 通过；真实链路 `scripts/e2e-sales.ps1` 8/8
  （支付回写、认领唯一、重复回调不加量、30 日接口与 SQL 对齐、商品列表对齐、匿名 401、买家 403）。
- 环境备注：本机 RocketMQ Broker 曾以默认 `C:\Users\<user>\store` 启动，C 盘占用超过
  `diskMaxUsedSpaceRatio` 后所有写入被拒（`message store is not writeable`）。启动时必须带
  `-c E:\rocketmq\conf\broker.conf`；JDK 21 在本机创建 selector 会触发 AF_UNIX 回环异常，
  Broker 改用 JDK 8 启动可正常工作。

## [0.9.0] - 2026-11-05

- 商品图上传（G-09）落地：`POST /api/admin/upload` 直传阿里云 OSS。
  - 仅 OPERATOR/ADMIN 可上传（BFF `@RequireStaff(OPERATOR)`）；匿名 401、买家 403。
  - 服务端按文件头（magic number）识别 JPG / PNG / WEBP / GIF，不信任扩展名或浏览器
    `Content-Type`；默认上限 5 MB，空文件与伪装图片分别返回 38001 / 38002 / 38003。
  - AccessKey 只从进程环境读取（`AccessKeyID` / `AccessKeySecret`），不下发浏览器；
    Bucket 默认公共读并返回稳定 HTTPS URL，避免签名过期导致商品主图破图；
    若确需私有桶，设 `OSS_PUBLIC_READ=false`，返回带签名临时 URL。
  - 发布/编辑商品页的主图字段改为「上传 + 预览 + 移除」，保留 URL 回显，编辑旧商品不受影响。
- 验证：`OssUploadServiceTest` 5 项；mall-admin 单测 36 项全绿；前端 `npm run build` 通过；
  真实 OSS E2E `scripts/e2e-upload.ps1` 5/5（匿名 401、买家 403、伪装图片 38003、
  管理员上传成功、URL 回读 200）。
- 配置：新增 `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_PUBLIC_READ` / `OSS_PUBLIC_BASE_URL` /
  `OSS_URL_EXPIRE_SECONDS` / `OSS_MAX_FILE_SIZE`；`start-all.ps1` 会从用户环境补齐 AccessKey。
## [0.8.0] - 2026-11-05

- 购物车模块 `mall-cart`（8109）交付，关闭 UC-CART-01 / 02 / 03 的后端缺口。
  - 存储：Redis Hash `mall:cart:{userId}`，字段 `qty:{skuId}` / `sel:{skuId}` / `at:{skuId}`；
    只保存用户选择，不缓存价格与库存，避免长时间不操作后的脏数据。
  - 加购与改数量走 Lua：并发下「读旧值 → 加一 → 写回」不会互相覆盖，数量上限、
    实时库存与购物车种类上限在同一原子操作内判定（单 SKU 99、最多 100 种、TTL 30 天）。
  - 每次读取实时刷新商品 / SKU / 上下架 / 库存；失效行保留并标记 `invalid`，
    由用户显式删除，不静默丢数据；金额与结算只统计「已勾选且有效」的行。
  - 库存服务不可用时返回 `DOWNSTREAM_UNAVAILABLE`（50001），不再把库存未知误判为售罄。
  - 批量改数量改为一次批量查库存，移除逐条查询的 N+1。
  - `allSelected` 只统计有效行，失效行不会让「全选」永远为 false。
  - 新增 `POST /api/cart/purchased/remove`，补齐下单成功后清理已购 SKU 的入口。
  - 身份只取网关注入的 `@CurrentUserId`，不接受客户端 `userId`；直连伪造 `X-User-Id`
    且内部令牌错误时按匿名处理（`20001`）；`/api/cart/**` 不在网关白名单。
  - 新增 7 个接口：查看、加购、批量改数量 / 勾选、批量勾选、结算预览、清理已购、删除单品。
- 为 C 端商品详情提供批量查询能力（`mall-api` 的 `ProductApi` / `InventoryApi` 与
  `mall-product` C 端批量接口），购物车一次读取即可刷新整车数据。
- 验证：`CartServiceTest` 15 项全绿；真实接口 E2E `scripts/e2e-cart.ps1` 11/11 通过
  （加购累加、上限、库存、售罄、勾选、金额、失效行、清理、匿名 401、伪造身份头）。
- 启动脚本修正：`start-all.ps1` 显式使用 `JAVA_HOME\bin\java.exe`，避免打印 JDK 21
  却实际用 PATH 中其他 JDK 启动；存在 `java.base` loopback 补丁时自动挂载。
- 文档：根 README、后端 README 与需求文档同步购物车状态；C 端购物车页面尚未接入。
## [0.7.0] - 2026-11-04

- 退款审核/驳回工作流（12.24 / 28.12 关闭主缺口）。
  - `POST /api/pay/refund` 新增可选 `audit`：`true` 只创建「0 待审核」退款单，
    不调渠道、不动支付单与订单；不传或 `false` 保持 V1.11 的「申请即退款」契约，
    C 端与 `scripts/e2e-pay.ps1` 不受影响。
  - 新增审核接口（BFF `POST /api/admin/refund/{refundNo}/approve|reject` →
    `mall-pay POST /api/pay/admin/refund/{refundNo}/approve|reject`）：
    同意走 `0 → 10 → 渠道退款 → 20`，支付单 20 → 50、订单 60 → 70；
    驳回走 `0 → 30`，原因落 `fail_reason`，`active_key` 置 NULL，订单保持 20 可重新申请。
  - 审批限定 `OPERATOR`（ADMIN 覆盖），CS 可查看但不能审批；BFF 与 `mall-pay` 双层校验，
    服务层再判一次 `hasRole(OPERATOR)`，避免新增入口漏注解。
  - 新增 `sql/13_refund_audit.sql`：`refund` 补 `audited_by / audited_by_id / audited_at`。
    刻意不复用 `operator / operator_id`——那是「发起方」，覆盖会丢掉原始申请人，
    正是资金纠纷最需要的审计信息。
  - 幂等查询 `selectActiveByOrderNo` 纳入 `status = 0`：审核期间重复申请回放同一单，
    不会产生第二张待审单；同意/驳回均为 CAS（0 → 10 / 0 → 30），重复点击幂等。
  - 同意前先校验支付单仍是已支付，再抢状态：避免「已抢到 10 但支付单已退」把单子永久卡在退款中。
  - 看板「待处理退款」改为 `refund.status in (0, 10)`，待办直达「待审核」Tab；
    审批动作只看 0，10/20/30 不再显示按钮，避免一次必然失败的请求。
  - 前端退款详情新增审核人/审核时间、条件化「同意退款 / 驳回」与驳回原因弹窗；
    客服视角显示「当前账号只能查看」说明。
  - `sql/07_seed_user_auth.sql` 增加 `cs01 / Cs@123456`（CS 角色），
    让「客服只读」验收可复现。
  - 新增 `RefundAuditServiceTest`（11 项）、`RefundMapperXmlTest`（5 项）与 BFF 4 项；
    后端全量单测 **129 项 / 22 suite 全绿**。
  - 真实接口 E2E 13 项：申请待审核不碰渠道、驳回后订单仍 20、可重新申请、
    同意后 20/50/70 联动、重复审批幂等、买家 403 / 匿名 401 / CS 403。
  - 新增 `npm run verify:refund-audit`（17 项）：待审核 Tab、审批按钮、驳回原因回显、
    同意二次确认、渠道单号回显、CS 只读；回归 `verify:refund` 与 `verify:dashboard` 通过。

## [0.6.0] - 2026-11-04

- UC-ORDER-06 确认收货（订单主流程闭环：待支付 → 已支付 → 已发货 → 已完成）。
  - 新增 `sql/12_order_completed.sql`：为 `orders` 补 `completed_at` 与
    `idx_orders_completed_at`，可重复执行。完成时间是售后期起算与结算对账的锚点，
    不能靠 `updated_at` 推断（任何后续更新都会覆盖它）。
  - 买家接口 `POST /api/order/{orderNo}/confirm`：已发货 → 已完成，仅订单本人可操作；
    重复确认与并发 CAS 失败均按幂等返回，不产生第二条状态日志。
  - 后台代客确认 `POST /api/admin/order/{orderNo}/confirm`（BFF）→
    `mall-order /api/order/admin/{orderNo}/confirm`：用于买家失联或物流长期未更新；
    限定 OPERATOR（ADMIN 覆盖），操作人取网关注入身份，日志 `operator = admin`。
  - 状态与完成时间在同一条 CAS SQL 写入，避免「状态已 40 但完成时间没落库」。
  - 订单 DTO 与后台 `AdminOrderRow` 增加 `completedAt`，详情页回显完成时间。
  - 前端订单详情在「已发货」状态显示「确认收货」按钮，带二次确认（终态不可逆）；
    完成后按钮消失，时间线新增一条记录。
  - 新增 `OrderConfirmServiceTest`（9 项）与 Mapper XML 的 `markCompleted` / baseColumns 断言；
    新增 `npm run verify:confirm`（真实造单 → 发货 → 代客确认，13 项全绿）。
  - 后端全量单测 96 项全绿（17 suite）；回归 login/order/refund/multitab/dashboard 全部通过。

## [vue-mall 0.2.0] - 2026-11-04

- 买家端阶段二：登录 / 注册 / 账户中心。
  - 新增买家端独立令牌存储、登录 / 注册 / 当前用户 / 注销适配层。
  - accessToken 过期或 20001 / 20002 时自动刷新并重放请求。
  - 新增单标签单飞 + Web Locks / localStorage 租约跨标签刷新互斥，避免后端重放检测全端下线。
  - 新增登录 / 注册 / 账户中心页面、认证表单校验、路由守卫、记住用户名与密码显隐。
  - 登录注册品牌布局与卡片抽为可复用组件；账户菜单支持点击外部与 Esc 关闭。
  - 新增 scripts/verify-auth.mjs 与 npm run verify:auth，覆盖守卫、错误密码、登录资料、刷新恢复、无感续期、移动布局、注销与注册校验。
  - vue-mall 版本更新为 0.2.0，同步更新前端架构与 README。


## [0.5.0] - 2026-11-04

- M5 数据看板与工作台指标（UC-ADMIN-03 / 计划文档 G-10）。
  - `mall-order` 新增 `GET /api/order/admin/dashboard/overview` 与
    `/api/order/admin/dashboard/trend?days=`：今日 GMV / 今日订单 / 待发货 / 退款中，
    以及近 N 日趋势。归日固定 `Asia/Shanghai`，趋势缺失日补 0，天数收敛到 1~90（默认 7）。
  - `mall-pay` 新增 `GET /api/pay/admin/refund/pending-count`；`mall-inventory` 新增
    `GET /api/inventory/admin/stat`，售罄（`total>0 && available=0`）与未备货（`total=0`）
    分开计数，避免把「没备货」误报成「售罄」。
  - `mall-admin` 新增 `GET /api/admin/dashboard?days=` 聚合 BFF：
    订单主指标失败即报错（不显示 0 误导运营）；退款与库存属辅助指标，下游不可用时置 0 并打 WARN。
  - 售罄率分母为 0 时返回 `null`，前端显示 “—” 而不是 0%。
  - 前端 `DashboardView` 重写为 4 张指标卡 + GMV/订单量 SVG 趋势 + 库存售罄率 + 待办；
    工作台接真实指标。**未引入 ECharts**：只有折线/网格/刻度，原生 SVG 足以覆盖，
    省掉约 300KB 依赖，后续出现复杂图表再评估。
  - 修复真实缺陷：看板待办跳转带 `?status=20`，但订单/退款列表页此前只读 `orderNo`/`refundNo`，
    忽略 `status`，导致落地后仍停在「全部」Tab。现两页均在 `onMounted` 解析 `status`
    并复用 Tab 的 watch 加载，非法值回退到全部。
  - 补齐测试与验收：新增 `AdminOrderDashboardServiceTest`、`InventoryMapperXmlTest`、
    订单看板 SQL 的 XML 断言与 `AdminDashboardServiceTest`；新增
    `npm run verify:dashboard`（17 项）并回归 login/order/refund/multitab。
  - 后端单测 85 项全绿；真实接口与 SQL 逐项对齐（GMV、订单量、趋势、库存统计）。


## [0.4.0] - 2026-11-04

- M4 收尾：多标签页令牌同步（计划文档 6.2 第 3 条硬约束）。
  - 新增 `front/vue-admin-web/src/api/refresh-lock.js`：跨标签互斥锁，
    优先使用 Web Locks API，不支持时退化为带过期时间与归属校验的 localStorage 租约锁。
  - `request.js` 刷新流程改为「本标签单飞 + 跨标签互斥」，抢到锁后先比对本地令牌，
    若其他标签已完成刷新则直接复用，**不再发第二次 `/auth/refresh`**；
    从而避免后端重放检测（旋转后旧令牌重放）导致的全端强制下线。
  - 修正刷新响应解析缺陷：响应拦截器已把 `Result` 解包为 `data`，旧代码又取了一次
    `res.data.data`，会让无感刷新恒失败。此前的 401 自动续期实际不可用。
  - 新增 `onTokenStorageChange()` 与 `userStore.syncTokens()`：一个标签刷新/登录/退出后，
    其他标签通过 `storage` 事件同步登录态；令牌被清除时一并回登录页，不整页 reload，
    避免丢失运营页面上的筛选条件。
  - 登录页 `redirect` 改为只接受站内绝对路径（拒绝 `https://...`、`//...`、`/\\...`），
    修掉开放重定向。
  - 新增 `npm run verify:multitab`：真实登录 + 双标签并发触发 401，
    断言只发生一次 refresh、两个标签均保持登录、退出后另一标签跟随回登录页（9 项全绿）。
  - `npm run verify:login` 扩充到 11 项：新增「带 redirect 登录回原页」与
    「站外 redirect 被忽略」两条回归用例。


## [0.3.0] - 2026-11-04

- M4 第二切片：订单发货 + 后台退款查询。
  - `mall-order` 新增 `POST /api/order/admin/{orderNo}/ship`：已支付 → 已发货，
    物流公司与单号与状态在同一条 CAS 中落库；重复提交同一单号幂等，改单号返回 `34002`；
    操作人取网关注入身份，接口要求 `@RequireStaff(OPERATOR)`。
  - 新增 `sql/11_order_shipping.sql`：为 `orders` 补 `shipping_company` / `tracking_no` /
    `shipped_at` / `shipped_by`，可重复执行（information_schema 检查后 ALTER）。
  - `mall-pay` 新增 `/api/pay/admin/refund/page` 与 `/{refundNo}`：全量退款单分页
    （退款单号 / 订单号 / 买家 / 状态 / 时间区间）与详情，类级 `@RequireStaff`。
  - `mall-admin` 新增订单发货转发与 `/api/admin/refund/**` BFF，均不降级、错误码原样透传。
  - 前端订单详情抽屉增加「发货」按钮与物流弹窗（仅 OPERATOR/ADMIN 可见）；
    退款页替换占位页，接真实分页、状态 Tab、筛选与详情抽屉。
  - 修正前端角色判定：`hasRole` 改为与后端一致的包含关系（ADMIN 覆盖 OPERATOR/CS），
    此前 admin 账号看不到发货按钮。
  - 修正 `REFUND_STATUS_DICT`：按后端 `RefundStatus` 改为 0/10/20/30，
    原先误用了支付单的 10/20/30/50。
  - 新增 22 个后端单测（mall-order 7 + mall-pay 7 + mall-admin 8），
    后端测试合计 47 个全绿；新增 `npm run verify:refund` 与 `scripts/seed-paid-order.mjs`，
    E2E 接口 15 项、UI 18 项全部通过。


## [0.2.0] - 2026-11-04

- 后台订单管理查询切片（M4 第一步）：
  - `mall-order` 新增后台订单分页（订单号/买家/状态/日期筛选）与运营视角详情（明细 + 状态流转时间线），类级 `@RequireStaff`；
  - `mall-admin` 新增订单 BFF（转发 + 错误码翻译，不做降级）；
  - 明细件数/数量一次批量聚合，避免逐单 N+1；
  - `front/vue-admin-web` 订单管理页接真实接口：状态 Tab、筛选、详情抽屉，新增 `npm run verify:order` UI 验收脚本；
  - 新增 17 个后端单测（含 Mapper XML 离线解析校验），E2E 14 项全部通过。
- M3 库存闭环归档：库存增量调整（幂等）、操作人审计流水、流水分页与管理页接通（此前未提交的工作区改动）。

## [0.1.2] - 2026-10-28

- 将后端 Maven 编译与运行基线调整为 JDK 21。
- 通过 JDK 21 重新执行全量 Maven 测试，避免本机 JDK 25 AF_UNIX loopback 补丁依赖。

## [0.1.1] - 2026-10-28

- 修复后台商品和目录读取接口缺少 staff 角色校验的问题。
- 收敛网关公开商品 GET 白名单，避免覆盖 `/api/product/admin/**` 管理路径。
- 为管理目录读取增加受保护的下游接口，并补充授权契约测试。
- 更新权限状态和集成验证说明。

## [0.1.0] - 2026-10-28

- 建立前后端单仓库的首个版本基线。
- 纳入 Spring Cloud Alibaba 商城微服务、运营管理后台、买家端脚手架、SQL 初始化脚本和项目文档。
- 配置敏感项改为由环境变量提供；增加本地配置模板与构建产物忽略规则。


