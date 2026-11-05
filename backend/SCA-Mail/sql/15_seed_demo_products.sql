-- 买家端首页补位演示商品：把前端「猜你喜欢」的展示位落成真实商品，
-- 保证卡片点击后进入 /product/:id 的真实详情，不再落到旧的功能预告页。
-- 全部语句可重复执行；图片沿用前端本地素材路径，由前端解析为静态资源。

USE `mall_product`;

INSERT INTO `category` (id, parent_id, name, level, sort_no, status) VALUES
  (4, 0, '家居', 1, 3, 1),
  (5, 0, '食品', 1, 4, 1),
  (6, 0, '美妆', 1, 5, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

INSERT INTO `product`
  (`id`, `name`, `subtitle`, `category_id`, `brand_id`, `main_image`, `price_min`, `price_max`, `sales`, `status`)
VALUES
  (101, '云感降噪头戴耳机', '40 小时续航 / 自适应降噪 / 轻至 238g', 1, 1, '/images/products/headphones.jpg', 499.00, 599.00, 12600, 1),
  (102, '轻量透气跑步鞋', '回弹中底 / 防滑大底 / 三色可选', 2, 2, '/images/products/sneakers.jpg', 269.00, 329.00, 8600, 1),
  (103, '奶油风休闲单椅', '高回弹坐垫 / 实木椅脚 / 久坐不累', 4, 2, '/images/products/chair.jpg', 899.00, 1099.00, 1200, 1),
  (104, '屏障修护保湿面霜', '神经酰胺 / 轻盈不黏腻 / 敏感肌友好', 6, 2, '/images/products/skincare.jpg', 189.00, 189.00, 23000, 1),
  (105, '复古微单相机套装', '4K 视频 / 五轴防抖 / 含定焦镜头', 1, 1, '/images/products/camera.jpg', 4299.00, 4699.00, 680, 1),
  (106, '手冲咖啡礼盒', '中深烘焙 / 坚果可可调 / 6 种风味', 5, 2, '/images/products/coffee.jpg', 129.00, 159.00, 5400, 1),
  (107, '通勤真皮托特包', '头层牛皮 / 大容量 / 可放 14 寸电脑', 2, 2, '/images/products/bag.jpg', 459.00, 459.00, 3100, 1),
  (108, '丝绒哑光唇釉', '低饱和玫瑰色 / 轻薄显色 / 不拔干', 6, 2, '/images/products/lipstick.jpg', 99.00, 129.00, 45000, 1),
  (109, '极简腕表 38mm', '蓝宝石镜面 / 真皮表带 / 50m 防水', 2, 2, '/images/products/watch.jpg', 799.00, 899.00, 960, 1),
  (110, '雪松木质淡香水', '前调佛手柑 / 中调雪松 / 尾调琥珀', 6, 2, '/images/products/perfume.jpg', 359.00, 359.00, 7200, 1),
  (111, '重磅纯棉基础 T 恤', '260g 精梳棉 / 不易变形 / 四色可选', 2, 2, '/images/products/tshirt.jpg', 89.00, 109.00, 18000, 1),
  (112, '客制化机械键盘', 'Gasket 结构 / 热插拔 / 三模连接', 1, 1, '/images/products/keyboard.jpg', 399.00, 599.00, 2400, 1),
  (113, '便携陶瓷咖啡杯', '手工釉面 / 350ml 两只装 / 可进洗碗机', 5, 2, '/images/products/coffee.jpg', 69.00, 89.00, 3300, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `subtitle` = VALUES(`subtitle`),
  `category_id` = VALUES(`category_id`),
  `brand_id` = VALUES(`brand_id`),
  `main_image` = VALUES(`main_image`),
  `price_min` = VALUES(`price_min`),
  `price_max` = VALUES(`price_max`),
  `sales` = VALUES(`sales`),
  `status` = VALUES(`status`),
  `deleted` = 0;

INSERT INTO `product_sku`
  (`id`, `product_id`, `sku_code`, `name`, `spec_json`, `price`, `market_price`, `image`, `status`)
VALUES
  (1101, 101, 'SKU-1101', '云感降噪头戴耳机 月岩灰', '{"颜色":"月岩灰"}', 499.00, 599.00, '/images/products/headphones.jpg', 1),
  (1102, 101, 'SKU-1102', '云感降噪头戴耳机 云白', '{"颜色":"云白"}', 599.00, 699.00, '/images/products/headphones.jpg', 1),
  (1103, 102, 'SKU-1103', '轻量透气跑步鞋 41 雾蓝', '{"尺码":"41","颜色":"雾蓝"}', 269.00, 329.00, '/images/products/sneakers.jpg', 1),
  (1104, 102, 'SKU-1104', '轻量透气跑步鞋 42 雾蓝', '{"尺码":"42","颜色":"雾蓝"}', 299.00, 359.00, '/images/products/sneakers.jpg', 1),
  (1105, 102, 'SKU-1105', '轻量透气跑步鞋 42 燕麦', '{"尺码":"42","颜色":"燕麦"}', 329.00, 389.00, '/images/products/sneakers.jpg', 1),
  (1106, 103, 'SKU-1106', '奶油风休闲单椅 米白', '{"颜色":"米白"}', 899.00, 1099.00, '/images/products/chair.jpg', 1),
  (1107, 103, 'SKU-1107', '奶油风休闲单椅 焦糖', '{"颜色":"焦糖"}', 1099.00, 1299.00, '/images/products/chair.jpg', 1),
  (1108, 104, 'SKU-1108', '屏障修护保湿面霜 50ml', '{"规格":"50ml"}', 189.00, 229.00, '/images/products/skincare.jpg', 1),
  (1109, 105, 'SKU-1109', '复古微单相机套装 单机身', '{"套装":"单机身"}', 4299.00, 4899.00, '/images/products/camera.jpg', 1),
  (1110, 105, 'SKU-1110', '复古微单相机套装 含定焦镜头', '{"套装":"含定焦镜头"}', 4699.00, 5299.00, '/images/products/camera.jpg', 1),
  (1111, 106, 'SKU-1111', '手冲咖啡礼盒 6 种风味', '{"规格":"6 种风味"}', 129.00, 159.00, '/images/products/coffee.jpg', 1),
  (1112, 106, 'SKU-1112', '手冲咖啡礼盒 12 种风味', '{"规格":"12 种风味"}', 159.00, 189.00, '/images/products/coffee.jpg', 1),
  (1113, 107, 'SKU-1113', '通勤真皮托特包 黑色', '{"颜色":"黑色"}', 459.00, 559.00, '/images/products/bag.jpg', 1),
  (1114, 108, 'SKU-1114', '丝绒哑光唇釉 #01 玫瑰', '{"色号":"#01 玫瑰"}', 99.00, 129.00, '/images/products/lipstick.jpg', 1),
  (1115, 108, 'SKU-1115', '丝绒哑光唇釉 #02 豆沙', '{"色号":"#02 豆沙"}', 129.00, 159.00, '/images/products/lipstick.jpg', 1),
  (1116, 109, 'SKU-1116', '极简腕表 38mm 棕色表带', '{"表带":"棕色","尺寸":"38mm"}', 799.00, 899.00, '/images/products/watch.jpg', 1),
  (1117, 109, 'SKU-1117', '极简腕表 38mm 黑色表带', '{"表带":"黑色","尺寸":"38mm"}', 899.00, 999.00, '/images/products/watch.jpg', 1),
  (1118, 110, 'SKU-1118', '雪松木质淡香水 50ml', '{"规格":"50ml"}', 359.00, 429.00, '/images/products/perfume.jpg', 1),
  (1119, 111, 'SKU-1119', '重磅纯棉基础 T 恤 白 M', '{"颜色":"白","尺码":"M"}', 89.00, 109.00, '/images/products/tshirt.jpg', 1),
  (1120, 111, 'SKU-1120', '重磅纯棉基础 T 恤 白 L', '{"颜色":"白","尺码":"L"}', 99.00, 119.00, '/images/products/tshirt.jpg', 1),
  (1121, 111, 'SKU-1121', '重磅纯棉基础 T 恤 黑 M', '{"颜色":"黑","尺码":"M"}', 109.00, 129.00, '/images/products/tshirt.jpg', 1),
  (1122, 112, 'SKU-1122', '客制化机械键盘 红轴', '{"轴体":"红轴"}', 399.00, 499.00, '/images/products/keyboard.jpg', 1),
  (1123, 112, 'SKU-1123', '客制化机械键盘 茶轴', '{"轴体":"茶轴"}', 499.00, 599.00, '/images/products/keyboard.jpg', 1),
  (1124, 112, 'SKU-1124', '客制化机械键盘 静音红轴', '{"轴体":"静音红轴"}', 599.00, 699.00, '/images/products/keyboard.jpg', 1),
  (1125, 113, 'SKU-1125', '便携陶瓷咖啡杯 米白两只装', '{"颜色":"米白","规格":"两只装"}', 69.00, 89.00, '/images/products/coffee.jpg', 1),
  (1126, 113, 'SKU-1126', '便携陶瓷咖啡杯 雾蓝两只装', '{"颜色":"雾蓝","规格":"两只装"}', 89.00, 109.00, '/images/products/coffee.jpg', 1)
ON DUPLICATE KEY UPDATE
  `product_id` = VALUES(`product_id`),
  `name` = VALUES(`name`),
  `spec_json` = VALUES(`spec_json`),
  `price` = VALUES(`price`),
  `market_price` = VALUES(`market_price`),
  `image` = VALUES(`image`),
  `status` = VALUES(`status`),
  `deleted` = 0;

USE `mall_inventory`;

INSERT INTO `inventory` (`sku_id`, `total`, `locked`, `available`, `warn_threshold`, `version`)
VALUES
  (1101, 120, 0, 120, 10, 0),
  (1102, 80, 0, 80, 10, 0),
  (1103, 90, 0, 90, 10, 0),
  (1104, 60, 0, 60, 10, 0),
  (1105, 45, 0, 45, 10, 0),
  (1106, 30, 0, 30, 5, 0),
  (1107, 20, 0, 20, 5, 0),
  (1108, 200, 0, 200, 20, 0),
  (1109, 15, 0, 15, 3, 0),
  (1110, 12, 0, 12, 3, 0),
  (1111, 150, 0, 150, 15, 0),
  (1112, 80, 0, 80, 10, 0),
  (1113, 70, 0, 70, 10, 0),
  (1114, 300, 0, 300, 30, 0),
  (1115, 260, 0, 260, 30, 0),
  (1116, 40, 0, 40, 5, 0),
  (1117, 35, 0, 35, 5, 0),
  (1118, 110, 0, 110, 10, 0),
  (1119, 180, 0, 180, 20, 0),
  (1120, 160, 0, 160, 20, 0),
  (1121, 140, 0, 140, 20, 0),
  (1122, 75, 0, 75, 10, 0),
  (1123, 60, 0, 60, 10, 0),
  (1124, 40, 0, 40, 5, 0),
  (1125, 220, 0, 220, 20, 0),
  (1126, 180, 0, 180, 20, 0)
ON DUPLICATE KEY UPDATE
  `total` = VALUES(`total`),
  `available` = VALUES(`available`),
  `warn_threshold` = VALUES(`warn_threshold`);
