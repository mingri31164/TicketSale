-- 订单服务数据库表字段更新脚本
-- 为订单表添加积分TCC事务ID字段

USE `12306_order_0`;
ALTER TABLE `t_order` ADD COLUMN `point_tcc_transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '积分TCC事务ID' AFTER `arrival_time`;

USE `12306_order_1`;
ALTER TABLE `t_order` ADD COLUMN `point_tcc_transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '积分TCC事务ID' AFTER `arrival_time`;

