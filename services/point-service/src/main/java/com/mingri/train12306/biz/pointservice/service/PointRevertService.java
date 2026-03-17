package com.mingri.train12306.biz.pointservice.service;

/**
 * 积分回退服务接口
 * 支持订单取消退款时的积分回退
 */
public interface PointRevertService {
    
    /**
     * 积分回退
     * 
     * @param userId 用户ID
     * @param orderSn 原订单号
     * @param revertPoint 回退积分数量
     * @return 回退结果
     */
    boolean revertPoint(Long userId, String orderSn, Integer revertPoint);
    
    /**
     * 查询用户已消耗积分的订单
     */
    java.util.List<com.mingri.train12306.biz.pointservice.dao.entity.UserPointSettlementDO> 
        queryConsumedOrders(Long userId);
}
