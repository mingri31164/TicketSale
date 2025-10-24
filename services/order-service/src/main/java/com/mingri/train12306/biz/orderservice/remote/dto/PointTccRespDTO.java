package com.mingri.train12306.biz.orderservice.remote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 积分TCC响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointTccRespDTO {
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 响应消息
     */
    private String message;
}

