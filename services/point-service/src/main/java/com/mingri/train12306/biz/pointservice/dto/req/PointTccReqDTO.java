package com.mingri.train12306.biz.pointservice.dto.req;

import lombok.Data;

/**
 * 积分TCC请求DTO
 */
@Data
public class PointTccReqDTO {
    
    /**
     * TCC全局事务ID
     */
    private String transactionId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 订单号
     */
    private String orderSn;
    
    /**
     * 积分数
     */
    private Integer point;
}

