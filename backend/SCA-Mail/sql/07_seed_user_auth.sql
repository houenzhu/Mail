-- 开发用种子账号：admin / buyer01 / operator01
-- 口令散列由 BCrypt(强度 10) 生成；手机号哈希 = SHA2(phone + salt, 256)
-- 默认 salt 为 mall-dev-phone-salt，必须与 mall.auth.phone-salt（或环境变量 PHONE_SALT）保持一致，
-- 否则按手机号查询会失配（按用户名登录不受影响）。
-- 默认口令：admin=Admin@123456，buyer01=Buyer@123456，operator01=Operator@123456

USE `mall_auth`;

INSERT INTO `user_account`
  (`user_id`, `username`, `phone`, `phone_hash`, `password_hash`, `nickname`, `roles`, `status`)
VALUES
  (10001, 'admin', '13800000001', SHA2(CONCAT('13800000001', 'mall-dev-phone-salt'), 256),
   '$2a$10$OSWGw.8ElBiY5Y1mA8ZEouwCed7STlUtaDc5ImflT31VeGxp0nvjK', '管理员', 'ADMIN,USER', 1),
  (10002, 'buyer01', '13800000010', SHA2(CONCAT('13800000010', 'mall-dev-phone-salt'), 256),
   '$2a$10$fBiG7KRWQrJLV5gHP6/QeOIHsaIBqDSidUtl4aQ0s7U6UOdSBFGx6', '小哲', 'USER', 1),
  (10003, 'operator01', '13800000011', SHA2(CONCAT('13800000011', 'mall-dev-phone-salt'), 256),
   '$2a$10$xXqAFj02Ywn82inA.O2Ml.gfREg49kfn0Tq6.A4ikTK1Bkf/aVxYK', '运营小张', 'OPERATOR,USER', 1)
ON DUPLICATE KEY UPDATE `user_id` = `user_id`;

USE `mall_user`;

INSERT INTO `user` (`id`, `username`, `nickname`, `phone`, `level`, `growth`, `points`, `status`)
VALUES
  (10001, 'admin', '管理员', '13800000001', 5, 10000, 5000, 1),
  (10002, 'buyer01', '小哲', '13800000010', 2, 1200, 320, 1),
  (10003, 'operator01', '运营小张', '13800000011', 3, 3000, 0, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;