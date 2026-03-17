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
-- 扩展：添加remainder(剩余积分)、source(来源)、code(类型编码)、expire_time(过期时间)、visible(是否可见)、update_time等字段
CREATE TABLE IF NOT EXISTS `t_user_point_detail` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `order_sn` VARCHAR(64) DEFAULT NULL COMMENT '关联订单号',
    `transaction_id` VARCHAR(64) NOT NULL COMMENT '事务ID（TCC全局事务ID）',
    `business_id` VARCHAR(64) DEFAULT NULL COMMENT '业务ID（幂等）',
    `delta` INT(11) NOT NULL COMMENT '积分变动值（正数=发放，负数=消耗）',
    `remainder` INT(11) NOT NULL DEFAULT 0 COMMENT '剩余积分（平账计算用，消耗时递减）',
    `state` VARCHAR(32) NOT NULL DEFAULT 'INIT' COMMENT '状态(0=INIT,1=PENDING,2=COMMITTED,3=CANCELED,4=REVERTED)',
    `type` VARCHAR(32) DEFAULT NULL COMMENT '积分类型（购票获得、抵扣等）',
    `source` VARCHAR(32) DEFAULT NULL COMMENT '积分来源（如：ORDER_REWARD, ACTIVITY, REFUND等）',
    `code` VARCHAR(32) DEFAULT NULL COMMENT '积分类型编码（如：FREEZE,CONSUME等）',
    `tag` VARCHAR(128) DEFAULT NULL COMMENT '标签',
    `message` VARCHAR(256) DEFAULT NULL COMMENT '变动说明',
    `ext` TEXT COMMENT '扩展信息（JSON格式，存放回退记录等）',
    `effective_time` DATETIME DEFAULT NULL COMMENT '生效时间',
    `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
    `visible` TINYINT DEFAULT 1 COMMENT '是否可见（1=可见，0=不可见）',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_sn` (`order_sn`),
    KEY `idx_transaction_id` (`transaction_id`),
    KEY `idx_business_id` (`business_id`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_remainder` (`remainder`),
    KEY `idx_create_time` (`create_time`),
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

-- 4. 积分对账/兑账表（核心新增）
-- 记录积分发放方与消费方的对应关系，是实现平账的核心依据
CREATE TABLE IF NOT EXISTS `t_user_point_settlement` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
    `consumer_source` VARCHAR(32) DEFAULT NULL COMMENT '消费方来源',
    `consumer_code` VARCHAR(32) DEFAULT NULL COMMENT '消费方编码',
    `provider_source` VARCHAR(32) DEFAULT NULL COMMENT '提供方来源',
    `provider_code` VARCHAR(32) DEFAULT NULL COMMENT '提供方编码',
    `consumer_transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '消费方事务ID',
    `consume_point` INT(11) NOT NULL DEFAULT 0 COMMENT '消耗积分（正数=核销，负数=回退）',
    `provider_business_id` VARCHAR(64) DEFAULT NULL COMMENT '提供方业务ID',
    `consumer_business_id` VARCHAR(64) DEFAULT NULL COMMENT '消费方业务ID',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_consumer_transaction_id` (`consumer_transaction_id`),
    KEY `idx_provider_business_id` (`provider_business_id`),
    KEY `idx_consumer_business_id` (`consumer_business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分对账/兑账表';

-- 5. 分表策略（可选，用于大数据量场景）
-- 按 user_id % 16 进行分表，表名: t_user_point_detail_0 ~ t_user_point_detail_15
-- 按 user_id % 16 进行分表，表名: t_user_point_settlement_0 ~ t_user_point_settlement_15

-- 插入测试数据（可选）
-- INSERT INTO `t_user_point` (`user_id`, `point`, `frozen_point`, `total_point`, `create_time`, `update_time`)
-- VALUES (1, 1000, 0, 1000, NOW(), NOW());

