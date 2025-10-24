-- 积分服务数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `12306_point` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `12306_point`;

-- 1. 用户积分账户表
CREATE TABLE IF NOT EXISTS `t_user_point` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `point` INT(11) NOT NULL DEFAULT 0 COMMENT '可用积分',
    `frozen_point` INT(11) NOT NULL DEFAULT 0 COMMENT '冻结积分',
    `total_point` INT(11) NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分账户表';

-- 2. 用户积分变动明细表
CREATE TABLE IF NOT EXISTS `t_user_point_detail` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `order_sn` VARCHAR(64) DEFAULT NULL COMMENT '关联订单号',
    `transaction_id` VARCHAR(64) NOT NULL COMMENT '事务ID（TCC全局事务ID）',
    `business_id` VARCHAR(64) DEFAULT NULL COMMENT '业务ID（幂等）',
    `delta` INT(11) NOT NULL COMMENT '积分变动值',
    `remainder` INT(11) NOT NULL COMMENT '变动后剩余积分',
    `state` VARCHAR(32) NOT NULL COMMENT '状态（TRY/CONFIRM/CANCEL）',
    `type` VARCHAR(32) DEFAULT NULL COMMENT '积分类型（购票获得、抵扣等）',
    `message` VARCHAR(256) DEFAULT NULL COMMENT '变动说明',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_sn` (`order_sn`),
    KEY `idx_transaction_id` (`transaction_id`),
    UNIQUE KEY `uk_business_id` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分变动明细表';

-- 3. 积分TCC事务记录表
CREATE TABLE IF NOT EXISTS `t_user_point_tcc` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `transaction_id` VARCHAR(64) NOT NULL COMMENT 'TCC全局事务ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `order_sn` VARCHAR(64) DEFAULT NULL COMMENT '关联订单号',
    `point` INT(11) NOT NULL COMMENT '涉及积分数',
    `action` VARCHAR(32) NOT NULL COMMENT '操作类型（FREEZE/CONSUME/GRANT）',
    `status` VARCHAR(32) NOT NULL COMMENT 'TCC状态（TRY/CONFIRM/CANCEL）',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transaction_id` (`transaction_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_sn` (`order_sn`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分TCC事务记录表';

-- 插入测试数据（可选）
-- INSERT INTO `t_user_point` (`user_id`, `point`, `frozen_point`, `total_point`, `create_time`, `update_time`)
-- VALUES (1, 1000, 0, 1000, NOW(), NOW());

