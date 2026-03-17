package com.mingri.train12306.biz.pointservice.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分对账/兑账表
 * 记录积分发放方与消费方的对应关系，是实现平账的核心依据
 */
@Data
@TableName("t_user_point_settlement")
public class UserPointSettlementDO {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 消费方来源
     */
    private String consumerSource;
    
    /**
     * 消费方编码
     */
    private String consumerCode;
    
    /**
     * 提供方来源
     */
    private String providerSource;
    
    /**
     * 提供方编码
     */
    private String providerCode;
    
    /**
     * 消费方事务ID
     */
    private String consumerTransactionId;
    
    /**
     * 消耗积分（正数=核销，负数=回退）
     */
    private Integer consumePoint;
    
    /**
     * 提供方业务ID
     */
    private String providerBusinessId;
    
    /**
     * 消费方业务ID
     */
    private String consumerBusinessId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
