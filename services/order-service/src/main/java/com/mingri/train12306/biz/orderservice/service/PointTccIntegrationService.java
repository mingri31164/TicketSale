package com.mingri.train12306.biz.orderservice.service;

/**
 * 积分TCC集成服务接口
 */
public interface PointTccIntegrationService {
    
    /**
     * 下单时冻结积分（Try阶段）
     * @param orderSn 订单号
     * @param userId 用户ID
     * @param orderAmount 订单金额（单位：分）
     * @return TCC事务ID
     */
    String freezePointOnCreateOrder(String orderSn, Long userId, Integer orderAmount);
    
    /**
     * 支付成功后发放积分（Confirm阶段）
     * @param tccTransactionId TCC事务ID
     */
    void grantPointOnPaySuccess(String tccTransactionId);
    
    /**
     * 支付失败或取消订单后归还积分（Cancel阶段）
     * @param tccTransactionId TCC事务ID
     */
    void cancelPointOnPayFail(String tccTransactionId);
}

