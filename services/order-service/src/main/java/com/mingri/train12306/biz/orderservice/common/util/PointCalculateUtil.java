package com.mingri.train12306.biz.orderservice.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 积分计算工具类
 */
public class PointCalculateUtil {
    
    /**
     * 积分兑换比例：1元 = 10积分
     */
    private static final BigDecimal POINT_RATE = new BigDecimal("10");
    
    /**
     * 根据订单金额计算应得积分
     * @param orderAmount 订单金额（单位：元）
     * @return 应得积分
     */
    public static Integer calculateEarnPoint(BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        // 订单金额 * 积分兑换比例，四舍五入
        return orderAmount.multiply(POINT_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
    
    /**
     * 根据订单金额计算应得积分（金额单位：分）
     * @param orderAmountCent 订单金额（单位：分）
     * @return 应得积分
     */
    public static Integer calculateEarnPointByCent(Integer orderAmountCent) {
        if (orderAmountCent == null || orderAmountCent <= 0) {
            return 0;
        }
        // 先转为元，再计算积分
        BigDecimal orderAmount = new BigDecimal(orderAmountCent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return calculateEarnPoint(orderAmount);
    }
}

