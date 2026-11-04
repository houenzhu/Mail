# 买家端前端（`vue-mall`）

好物集商城 C 端，基于 Vue 3 + Vite + Pinia + Vue Router。当前完成**阶段一：首页**，并已接入真实商品接口，接口不可用时自动降级为本地演示数据。

## 快速开始

```bash
npm install
npm run dev      # http://127.0.0.1:5175，/api 代理到 127.0.0.1:10000
npm run build    # 生产构建
npm run verify   # 无头浏览器截图验收（需先启动 dev）
```

## 已实现页面

| 路由 | 页面 | 状态 |
| :--- | :--- | :--- |
| `/` | 首页（分类导航、轮播、活动入口、服务保障、猜你喜欢、场景展示） | 已完成 |
| `/search?q=` | 搜索结果（真实接口、综合/销量排序、空态与错误态） | 已完成 |
| `/feature/:feature` | 功能预告（登录、购物车、订单等未上线入口） | 已完成 |
| `*` | 404 | 已完成 |

## 目录约定

- `src/api`：接口适配层。字段映射集中在此，换后端只改一处。
- `src/components/common`：无业务语义的通用组件，可在任意页面复用。
- `src/components/layout`：页头、页脚、搜索、悬浮工具条。
- `src/components/product`：商品卡片与网格，首页和搜索页共用。
- `src/components/home`：首页专属分区组件。
- `src/composables`：数据聚合与轮播状态机，视图不直接取数。
- `src/data`：演示内容与本地兜底商品。
- `docs/frontend-architecture.md`：完整组件划分、设计令牌、数据契约与分阶段规划。

## 接口说明

公开接口（经网关）：

- `GET /api/product/page?pageNo=&pageSize=&keyword=&status=1`
- `GET /api/product/category/list`
- `GET /api/product/{id}/detail`

首页调用商品分页；真实数据不足一屏时使用平台精选补足，并在页面上以「部分为平台精选」标注，不冒充真实库存。

## 已知边界

- 登录、购物车、下单、商品详情等入口指向功能预告页，属于后续阶段。
- 后端种子数据中的 `img.example.com` 为占位域名，前端映射到本地图片，避免无效请求。
- 图片素材来自 Unsplash，仅作演示，不用于商业分发。

## 下一阶段

第二阶段：登录 / 注册、用户态持久化、路由守卫与个人中心骨架。现有 `SiteHeader` 的登录、收藏、购物车入口已预留统一跳转，接入时替换为真实路由与 Pinia store 即可。
