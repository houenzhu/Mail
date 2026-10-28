USE `mall_product`;

INSERT INTO `category` (id, parent_id, name, level, sort_no, status) VALUES
  (1, 0, '数码', 1, 1, 1),
  (2, 0, '服饰', 1, 2, 1),
  (3, 1, '耳机', 2, 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO `brand` (id, name, logo_url, sort_no, status) VALUES
  (1, 'ZheTech', 'https://img.example.com/brand/1.png', 1, 1),
  (2, 'ZheStyle', 'https://img.example.com/brand/2.png', 2, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO `product` (id, name, subtitle, category_id, brand_id, main_image, price_min, price_max, sales, status) VALUES
  (1, '无线降噪耳机 Pro', '40小时续航 / 主动降噪', 3, 1, 'https://img.example.com/product/1.png', 499.00, 519.00, 120, 1),
  (2, '纯棉圆领T恤', '夏季新款 / 透气亲肤', 2, 2, 'https://img.example.com/product/2.png', 79.00, 79.00, 860, 1),
  (3, '机械键盘 87键', '库存为 0，用于验证售罄', 1, 1, 'https://img.example.com/product/3.png', 299.00, 299.00, 5, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO `product_sku` (id, product_id, sku_code, name, spec_json, price, market_price, image, status) VALUES
  (1001, 1, 'SKU-1001', '降噪耳机 白色', '{"颜色":"白"}', 499.00, 599.00, 'https://img.example.com/sku/1001.png', 1),
  (1002, 1, 'SKU-1002', '降噪耳机 黑色', '{"颜色":"黑"}', 519.00, 619.00, 'https://img.example.com/sku/1002.png', 1),
  (1003, 2, 'SKU-1003', 'T恤 白色 M', '{"颜色":"白","尺码":"M"}', 79.00, 99.00, 'https://img.example.com/sku/1003.png', 1),
  (1004, 2, 'SKU-1004', 'T恤 白色 L', '{"颜色":"白","尺码":"L"}', 79.00, 99.00, 'https://img.example.com/sku/1004.png', 1),
  (1005, 3, 'SKU-1005', '机械键盘 87键 黑色', '{"颜色":"黑","轴体":"红轴"}', 299.00, 359.00, 'https://img.example.com/sku/1005.png', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

USE `mall_inventory`;

INSERT INTO `inventory` (sku_id, total, locked, available, warn_threshold, version) VALUES
  (1001, 100, 0, 100, 10, 0),
  (1002, 50, 0, 50, 10, 0),
  (1003, 200, 0, 200, 20, 0),
  (1004, 0, 0, 0, 0, 0),
  (1005, 0, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE total = VALUES(total), available = VALUES(available), version = 0;
