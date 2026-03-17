package com.mingri.train12306.biz.pointservice.service;

import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;

import java.util.List;

/**
 * 积分消耗服务接口
 * 实现双向指针匹配算法进行积分消耗
 */
public interface PointConsumeService {
    
    /**
     * 积分消耗（核销）
     * 使用双向指针匹配算法实现最优匹配
     * 
     * @param userId 用户ID
     * @param orderSn 订单号
     * @param consumePoint 消耗积分数量（正数）
     * @param source 消费方来源
     * @param code 消费方编码
     * @return 消耗结果
     */
    boolean consumePoint(Long userId, String orderSn, Integer consumePoint, String source, String code);
    
    /**
     * 查询用户可用积分总额
     */
    Integer queryAvailablePoint(Long userId);
}
