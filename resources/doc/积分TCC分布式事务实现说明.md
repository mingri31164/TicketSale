# 12306购票积分服务TCC分布式事务实现说明

## 一、项目概述

本文档说明了如何在12306购票系统中集成积分服务，并通过TCC分布式事务框架保证积分业务的一致性。

## 二、功能设计

### 2.1 业务流程

1. **下单阶段（Try）**：用户下单时，系统冻结用户应得积分（购票金额 * 积分比例）。
2. **支付成功（Confirm）**：用户支付成功后，将冻结积分转为可用积分，正式发放给用户。
3. **支付失败/取消订单（Cancel）**：用户支付失败或取消订单时，归还冻结积分。

### 2.2 积分计算规则

- 积分兑换比例：**1元 = 10积分**
- 订单金额单位：分
- 积分计算公式：积分 = (订单金额 / 100) * 10，四舍五入

## 三、数据表设计

### 3.1 用户积分账户表（t_user_point）

记录每个用户的积分账户信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| user_id | BIGINT | 用户ID（唯一） |
| point | INT | 可用积分 |
| frozen_point | INT | 冻结积分 |
| total_point | INT | 累计获得积分 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### 3.2 用户积分变动明细表（t_user_point_detail）

记录每一次积分变动的流水明细。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| user_id | BIGINT | 用户ID |
| order_sn | VARCHAR(64) | 关联订单号 |
| transaction_id | VARCHAR(64) | TCC事务ID |
| business_id | VARCHAR(64) | 业务ID（唯一，幂等） |
| delta | INT | 积分变动值 |
| remainder | INT | 变动后剩余积分 |
| state | VARCHAR(32) | 状态（TRY/CONFIRM/CANCEL） |
| type | VARCHAR(32) | 积分类型 |
| message | VARCHAR(256) | 变动说明 |
| create_time | DATETIME | 创建时间 |

### 3.3 积分TCC事务记录表（t_user_point_tcc）

记录TCC事务的状态。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| transaction_id | VARCHAR(64) | TCC事务ID（唯一） |
| user_id | BIGINT | 用户ID |
| order_sn | VARCHAR(64) | 关联订单号 |
| point | INT | 涉及积分数 |
| action | VARCHAR(32) | 操作类型（FREEZE/CONSUME） |
| status | VARCHAR(32) | TCC状态（TRY/CONFIRM/CANCEL） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### 3.4 订单表字段扩展（t_order）

在订单表中新增字段用于存储TCC事务ID。

| 字段 | 类型 | 说明 |
|------|------|------|
| point_tcc_transaction_id | VARCHAR(64) | 积分TCC事务ID |

## 四、TCC三阶段实现

### 4.1 Try阶段（冻结积分）

**时机**：用户下单时

**操作**：
1. 根据订单金额计算应得积分
2. 生成TCC全局事务ID
3. 调用积分服务的Try接口，冻结积分
4. 将TCC事务ID保存到订单表

**代码位置**：
- `OrderServiceImpl.createTicketOrder()`
- `PointTccServiceImpl.tryFreezePoint()`

### 4.2 Confirm阶段（发放积分）

**时机**：用户支付成功

**操作**：
1. 从订单表中获取TCC事务ID
2. 调用积分服务的Confirm接口
3. 扣减冻结积分，增加可用积分
4. 记录积分变动明细

**代码位置**：
- `OrderServiceImpl.payCallbackOrder()`
- `PointTccServiceImpl.confirmGrantPoint()`

### 4.3 Cancel阶段（归还积分）

**时机**：用户支付失败或取消订单

**操作**：
1. 从订单表中获取TCC事务ID
2. 调用积分服务的Cancel接口
3. 扣减冻结积分（归还）
4. 记录积分变动明细

**代码位置**：
- `OrderServiceImpl.cancelTickOrder()`
- `PointTccServiceImpl.cancelFreezePoint()`

## 五、核心代码说明

### 5.1 积分TCC服务接口

```java
public interface PointTccService {
    // Try：冻结积分
    boolean tryFreezePoint(String transactionId, Long userId, String orderSn, Integer point);
    
    // Confirm：确认发放积分
    boolean confirmGrantPoint(String transactionId);
    
    // Cancel：取消冻结，归还积分
    boolean cancelFreezePoint(String transactionId);
}
```

### 5.2 订单服务集成

```java
// 下单时
String pointTccTransactionId = pointTccIntegrationService.freezePointOnCreateOrder(
    orderSn, userId, totalAmount);

// 支付成功时
pointTccIntegrationService.grantPointOnPaySuccess(pointTccTransactionId);

// 取消订单时
pointTccIntegrationService.cancelPointOnPayFail(pointTccTransactionId);
```

## 六、部署说明

### 6.1 数据库初始化

1. 执行 `resources/db/12306_point.sql`，创建积分服务数据库和表。
2. 执行 `resources/db/12306_order_add_point_tcc.sql`，为订单表添加TCC事务ID字段。

### 6.2 服务启动

1. 启动Nacos注册中心
2. 启动Redis
3. 启动 `point-service`（端口：9300）
4. 启动 `order-service`

### 6.3 配置说明

**point-service配置（application.yaml）：**
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/12306_point
```

**order-service配置（需添加）：**
```yaml
point:
  service:
    url: http://localhost:9300  # 本地开发环境
    # url: "" # 生产环境使用服务发现
```

## 七、TCC事务一致性保证

### 7.1 幂等性保证

- 通过 `transaction_id` 做唯一约束，防止重复执行。
- 每次Try前先检查是否已存在相同事务ID。

### 7.2 状态流转

```
Try -> Confirm  （正常流程）
Try -> Cancel   （异常流程）
```

### 7.3 异常处理

- Try阶段失败：订单创建失败，不影响用户。
- Confirm阶段失败：记录日志，可通过补偿机制重试。
- Cancel阶段失败：记录日志，可通过补偿机制重试。

## 八、API接口说明

### 8.1 积分TCC接口

**服务地址**：`http://localhost:9300`

#### Try接口

```
POST /api/point/tcc/freeze/try
Content-Type: application/json

{
  "transactionId": "TCC_xxx",
  "userId": 1,
  "orderSn": "1234567890",
  "point": 100
}
```

#### Confirm接口

```
POST /api/point/tcc/freeze/confirm?transactionId=TCC_xxx
```

#### Cancel接口

```
POST /api/point/tcc/freeze/cancel?transactionId=TCC_xxx
```

## 九、测试场景

### 9.1 正常购票流程

1. 用户下单，订单金额100元
2. 系统冻结1000积分（100 * 10）
3. 用户支付成功
4. 系统发放1000积分到可用积分

**验证**：
- 订单表中 `point_tcc_transaction_id` 字段有值
- 积分账户表中冻结积分和可用积分变化正确
- 积分明细表中有对应的流水记录

### 9.2 取消订单流程

1. 用户下单，订单金额100元
2. 系统冻结1000积分
3. 用户取消订单
4. 系统归还1000冻结积分

**验证**：
- 积分账户表中冻结积分恢复
- 积分明细表中有取消记录

### 9.3 支付超时流程

1. 用户下单，订单金额100元
2. 系统冻结1000积分
3. 订单超时自动关闭
4. 系统归还1000冻结积分

**验证**：
- 通过延时消息触发订单关闭
- 积分自动归还

## 十、注意事项

1. **分布式锁**：订单操作和积分操作都使用了分布式锁，保证并发安全。
2. **事务一致性**：Try、Confirm、Cancel都必须在事务中执行。
3. **补偿机制**：建议添加定时任务，扫描长时间处于Try状态的事务，进行补偿。
4. **监控告警**：建议对TCC各阶段的成功率、耗时等指标进行监控。
5. **日志记录**：所有TCC操作都有详细日志，便于问题排查。

## 十一、扩展功能

### 11.1 积分抵扣

可以基于现有TCC框架实现积分抵扣功能：

```java
// Try：冻结用户可用积分
pointTccService.tryConsumePoint(transactionId, userId, orderSn, point);

// Confirm：正式扣减积分
pointTccService.confirmConsumePoint(transactionId);

// Cancel：归还积分
pointTccService.cancelConsumePoint(transactionId);
```

### 11.2 积分转账

可以实现用户之间的积分转账功能，同样使用TCC保证一致性。

## 十二、总结

本方案通过TCC分布式事务框架，实现了购票积分的一致性保证：
- **Try阶段**：预留资源（冻结积分）
- **Confirm阶段**：确认操作（发放积分）
- **Cancel阶段**：撤销操作（归还积分）

通过明确的状态流转和幂等性保证，确保在分布式环境下积分数据的准确性和一致性。

