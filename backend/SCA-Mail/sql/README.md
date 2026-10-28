# 数据库脚本

按服务分库（database-per-service），每个服务只访问自己的库，跨服务数据通过接口获取，不做跨库 JOIN。

| 文件 | 库 | 表 |
| :--- | :--- | :--- |
| `01_mall_product.sql` | `mall_product` | `category`、`brand`、`product`、`product_sku` |
| `02_mall_inventory.sql` | `mall_inventory` | `inventory`、`inventory_record` |
| `03_mall_order.sql` | `mall_order` | `orders`、`order_item`、`order_status_log` |
| `04_seed_data.sql` | 上述三个库 | 开发用种子数据（分类、品牌、商品、SKU、库存），可重复执行 |
| `05_mall_user.sql` | `mall_user` | `user`（用户档案） |
| `06_mall_auth.sql` | `mall_auth` | `user_account`（账号凭证）、`refresh_token`（刷新令牌轮换登记）、`token_blacklist`（Sa-Token 迁移前遗留表，已停用）、`login_log`（登录审计） |
| `07_seed_user_auth.sql` | `mall_auth` / `mall_user` | 开发用种子账号（admin / buyer01 / operator01），可重复执行 |
| `08_mall_pay.sql` | `mall_pay` | `payment`（支付单）、`payment_callback_log`（回调流水）、`refund`（退款单）、`pay_reconcile_diff`（对账差异），见需求文档 28.3 |
| `09_mall_mq.sql` | `mall_order` / `mall_pay` / `mall_inventory` | `local_message`（本地消息表 Outbox，落在生产方库 order / pay）、`consumed_message`（消费幂等表，落在消费方库 inventory / order / pay），见需求文档 19.6 / 20.3 |

## 执行方式

```powershell
# 需要 MySQL 客户端，默认读取 D:\mysql 下的 8.0.32 安装目录
$env:MYSQL_PASSWORD = '你的密码'
powershell -ExecutionPolicy Bypass -File .\scripts\init-db.ps1

# 或显式指定
powershell -ExecutionPolicy Bypass -File .\scripts\init-db.ps1 -MysqlHome 'D:\mysql\mysql-8.0.32-winx64\mysql-8.0.32-winx64' -Password '你的密码'
```

脚本幂等：`CREATE DATABASE IF NOT EXISTS` + `CREATE TABLE IF NOT EXISTS`，可重复执行；不会删除或覆盖已有数据。

## 约定

- 字符集统一 `utf8mb4` / `utf8mb4_0900_ai_ci`，引擎 `InnoDB`。
- 所有表带 `created_at` / `updated_at`（`DATETIME(3)`，`ON UPDATE` 自动维护）。
- 主数据表（`category` / `brand` / `product` / `product_sku`）用自增主键；**订单与流水表的主键由应用生成雪花 ID**（`orders` 用 `order_no` 业务主键），便于后续按 `user_id` 分库分表。
- 金额一律 `DECIMAL(12,2)`，禁止浮点；状态一律 `TINYINT` + 枚举映射。
- 幂等依赖唯一索引：`orders(user_id, request_id)`、`inventory_record(order_no, sku_id, type)`、`product_sku(sku_code)`、`payment(order_no, request_id)`、`payment_callback_log(channel, channel_trade_no, event_type)`。
- 分片预留：`orders` / `order_item` 以 `user_id` 为分片键，`order_item` 冗余 `user_id` 保证同分片；当前为单库单表，物理分片在确定方案后由 ShardingSphere 接管（见需求文档 12.4）。
- 库存表带 `CHECK (available >= 0)` 兜底，业务层仍以条件更新（`WHERE available >= ?`）为准。


## 认证与用户档案的分工（ADR-009）

- `mall_auth.user_account` 只存认证与风控字段：口令散列（BCrypt 强度 10）、角色、锁定状态、失败计数；`mall_user.user` 存展示档案（昵称、等级、成长值、积分），两侧以 `user_id` 一一对应。
- 手机号同时存明文与 `phone_hash = SHA2(CONCAT(phone, salt), 256)`，唯一索引与查询走哈希列，默认盐 `mall-dev-phone-salt` 必须与 `mall.auth.phone-salt` 一致（否则按手机号查询失配，按用户名登录不受影响）。
- 注册顺序：**先建档案、后建账号**（先 Feign 调 `mall_user`，再本地插账号），避免出现「有账号无档案」；账号插入失败可能留下孤儿档案，由后续对账任务清理（见需求文档 26.6）。
- `token_blacklist` 是旧自研令牌实现留下的兼容/历史表；当前注销通过删除 Sa-Token 会话立即失效，不再写入这张表。表仍保留在建表脚本中，不代表黑名单仍参与鉴权。Sa-Token 迁移细节见需求文档 21.9。

## 支付库（`mall_pay`，V1.11 落地）

- 一个订单可有多张支付单（`payment.order_no` 1:N），幂等键是唯一索引 `uk_order_request(order_no, request_id)`；同一订单只允许一张未关闭支付单，由发起支付时 CAS 关闭旧单保证（需求文档 28.5）。
- 回调幂等靠 `payment_callback_log.uk_event(channel, channel_trade_no, event_type)`：重复投递复用原流水行改 `process_result`，**不新增行**（需求文档 28.6）。
- 退款幂等靠 `refund.active_key` 唯一索引：成功 / 处理中只允许一条（`active_key = payment_no`），失败置 NULL 允许重试（需求文档 28.7）。
- 对账差异靠 `pay_reconcile_diff.uk_diff`：重复跑同日对账不重复落差异（需求文档 28.8）。
- 金额一律 `DECIMAL(12,2)`；`channel_trade_no` 在失败留痕场景写 NULL，避免占住唯一索引。

## 消息表（`09_mall_mq.sql`，第三阶段第 7 项 RocketMQ）

- **不建独立的 `mall_mq` 库**：Outbox 必须与业务写同库同事务，否则会出现「业务已提交、消息未落库」的窗口，RPO=0 即失效（需求文档 6.2 / 19.6）。因此 `local_message` 分别落在生产方库 `mall_order` / `mall_pay`。
- `local_message.status`：`0 待发送 / 1 已发送 / 2 死信`；`retry_count` 达 3 次置死信并记录错误日志/死信计数，当前没有自动告警通道（见 29.8）。
- `local_message.delay_level`：RocketMQ 延时级别，`0` 立即发送，`16` = 30 分钟（超时关单，见 19.6 `TOPIC_ORDER_TIMEOUT`）。
- `consumed_message.msg_id` 为主键：先插入成功者才执行业务，重复投递直接跳过；业务唯一索引（`uk_user_request` / `uk_order_sku_type` 等）是第二道防线。
- 幂等键口径（19.6）：`TOPIC_ORDER_CREATED` / `TOPIC_ORDER_TIMEOUT` / `TOPIC_ORDER_CANCELLED` 用 `orderNo`；`TOPIC_PAY_SUCCESS` 用 `paymentNo`；`TOPIC_STOCK_ROLLBACK` 用 `orderNo + type`。

## 待补充

`mall_user.user_address`（收货地址）与积分流水、`mall_promotion`（优惠券、秒杀活动）、`mall_cart`、`mall_comment` 的建表脚本随对应阶段补充。

客服会话、工单与 AI Agent 知识库目前没有数据库表或持久化实现；若后续确定采用客服 Agent，应先按需求文档 30.3 定义保留期限、脱敏和访问控制，再单独设计 schema。不要把用户订单/支付明细复制进向量库充当客服知识。
