package com.mingri.train12306.biz.pointservice.service;

/**
 * 积分TCC服务接口
 */
public interface PointTccService {
    
    /**
     * Try：冻结积分（购票获得积分场景）
     * @param transactionId TCC全局事务ID
     * @param userId 用户ID
     * @param orderSn 订单号
     * @param point 积分数
     * @return 是否成功
     */
    boolean tryFreezePoint(String transactionId, Long userId, String orderSn, Integer point);
    
    /**
     * Confirm：确认发放积分
     * @param transactionId TCC全局事务ID
     * @return 是否成功
     */
    boolean confirmGrantPoint(String transactionId);
    
    /**
     * Cancel：取消冻结，归还积分
     * @param transactionId TCC全局事务ID
     * @return 是否成功
     */
    boolean cancelFreezePoint(String transactionId);
    
    /**
     * Try：冻结并扣减积分（积分抵扣场景）
     * @param transactionId TCC全局事务ID
     * @param userId 用户ID
     * @param orderSn 订单号
     * @param point 积分数
     * @return 是否成功
     */
    boolean tryConsumePoint(String transactionId, Long userId, String orderSn, Integer point);
    
    /**
     * Confirm：确认扣减积分
     * @param transactionId TCC全局事务ID
     * @return 是否成功
     */
    boolean confirmConsumePoint(String transactionId);
    
    /**
     * Cancel：归还扣减的积分
     * @param transactionId TCC全局事务ID
     * @return 是否成功
     */
    boolean cancelConsumePoint(String transactionId);
}

