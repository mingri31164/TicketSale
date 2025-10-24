# 12306购票积分服务TCC分布式事务实现 - 文件清单

## 一、新建文件列表

### 1. point-service模块

#### 1.1 项目配置
- `services/point-service/pom.xml` - Maven配置文件
- `services/point-service/src/main/resources/application.yaml` - 应用配置文件

#### 1.2 实体类（Entity）
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dao/entity/UserPointDO.java` - 用户积分账户实体
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dao/entity/UserPointDetailDO.java` - 积分明细实体
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dao/entity/UserPointTccDO.java` - TCC事务记录实体

#### 1.3 Mapper层
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dao/mapper/UserPointMapper.java` - 积分账户Mapper
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dao/mapper/UserPointDetailMapper.java` - 积分明细Mapper
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dao/mapper/UserPointTccMapper.java` - TCC事务Mapper

#### 1.4 Mapper XML
- `services/point-service/src/main/resources/mapper/UserPointMapper.xml` - 积分账户Mapper XML
- `services/point-service/src/main/resources/mapper/UserPointDetailMapper.xml` - 积分明细Mapper XML
- `services/point-service/src/main/resources/mapper/UserPointTccMapper.xml` - TCC事务Mapper XML

#### 1.5 枚举类
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/common/enums/PointTccActionEnum.java` - TCC操作类型枚举
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/common/enums/PointTccStatusEnum.java` - TCC状态枚举

#### 1.6 Service层
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/service/PointTccService.java` - TCC服务接口
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/service/impl/PointTccServiceImpl.java` - TCC服务实现

#### 1.7 DTO层
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dto/req/PointTccReqDTO.java` - TCC请求DTO
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/dto/resp/PointTccRespDTO.java` - TCC响应DTO

#### 1.8 Controller层
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/controller/PointTccController.java` - TCC控制器

#### 1.9 启动类
- `services/point-service/src/main/java/com/mingri/train12306/biz/pointservice/PointServiceApplication.java` - 应用启动类

### 2. order-service模块集成

#### 2.1 远程调用
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/remote/PointRemoteService.java` - 积分服务远程调用接口
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/remote/dto/PointTccReqDTO.java` - 远程调用请求DTO
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/remote/dto/PointTccRespDTO.java` - 远程调用响应DTO

#### 2.2 工具类
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/common/util/PointCalculateUtil.java` - 积分计算工具类

#### 2.3 集成服务
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/service/PointTccIntegrationService.java` - 积分TCC集成服务接口
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/service/impl/PointTccIntegrationServiceImpl.java` - 积分TCC集成服务实现

### 3. 数据库脚本

- `resources/db/12306_point.sql` - 积分服务数据库初始化脚本
- `resources/db/12306_order_add_point_tcc.sql` - 订单表字段扩展脚本

### 4. 文档

- `resources/doc/积分TCC分布式事务实现说明.md` - 详细实现说明文档
- `resources/doc/积分TCC实现文件清单.md` - 本文件

## 二、修改文件列表

### 1. 项目配置
- `services/pom.xml` - 添加point-service模块

### 2. 实体类
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/dao/entity/OrderDO.java` - 添加pointTccTransactionId字段

### 3. 服务实现
- `services/order-service/src/main/java/com/mingri/train12306/biz/orderservice/service/impl/OrderServiceImpl.java` - 集成积分TCC三阶段

## 三、核心功能实现

### 3.1 TCC三阶段

| 阶段 | 触发时机 | 核心方法 | 操作 |
|------|---------|---------|------|
| Try | 用户下单 | `tryFreezePoint()` | 冻结应得积分 |
| Confirm | 支付成功 | `confirmGrantPoint()` | 发放积分 |
| Cancel | 取消订单 | `cancelFreezePoint()` | 归还积分 |

### 3.2 积分计算

- 比例：1元 = 10积分
- 工具类：`PointCalculateUtil`
- 计算方法：`calculateEarnPointByCent(Integer orderAmountCent)`

### 3.3 幂等性保证

- 通过 `transaction_id` 唯一约束
- 每次操作前检查是否已存在

### 3.4 分布式调用

- 使用 OpenFeign 远程调用
- 支持服务发现（Nacos）
- 支持本地直连（开发环境）

## 四、部署步骤

### 4.1 数据库准备

```bash
# 1. 创建积分服务数据库
mysql -u root -p < resources/db/12306_point.sql

# 2. 更新订单表字段
mysql -u root -p < resources/db/12306_order_add_point_tcc.sql
```

### 4.2 启动服务

```bash
# 1. 启动积分服务
cd services/point-service
mvn spring-boot:run

# 2. 启动订单服务
cd services/order-service
mvn spring-boot:run
```

## 五、测试验证

### 5.1 测试场景

1. **正常购票**：下单 -> 支付成功 -> 积分发放
2. **取消订单**：下单 -> 取消订单 -> 积分归还
3. **支付超时**：下单 -> 超时关闭 -> 积分归还

### 5.2 验证点

- 订单表 `point_tcc_transaction_id` 字段
- 积分账户表余额变化
- 积分明细表流水记录
- TCC事务表状态流转

## 六、技术栈

- Spring Boot 3.x
- MyBatis Plus
- MySQL 8.0
- Redisson（分布式锁）
- OpenFeign（远程调用）
- Nacos（服务发现）
- RocketMQ（消息队列）

## 七、设计亮点

1. **TCC模式**：明确的三阶段状态流转
2. **幂等性保证**：transaction_id唯一约束
3. **分布式锁**：保证并发安全
4. **状态记录**：完整的流水明细
5. **远程调用**：服务解耦，易于扩展
6. **异常处理**：完善的日志和异常处理机制

## 八、注意事项

1. 积分服务和订单服务必须同时启动
2. 数据库连接配置需根据实际环境调整
3. 建议添加补偿机制处理长时间未完成的TCC事务
4. 建议添加监控告警
5. 生产环境建议使用服务发现而非直连

## 九、后续扩展

1. **积分抵扣**：已实现接口，可直接使用
2. **积分转账**：可基于现有TCC框架实现
3. **积分过期**：可添加定时任务处理
4. **积分等级**：可扩展用户积分等级体系
5. **积分商城**：可实现积分兑换商品功能

