# 更新日志

本项目使用语义化版本。每个正式基线或发布都在此记录；未发布改动先记入「未发布」。

## 未发布

暂无。


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


