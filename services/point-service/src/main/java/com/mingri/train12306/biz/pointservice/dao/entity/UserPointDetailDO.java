package com.mingri.train12306.biz.pointservice.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户积分变动明细表
 */
@Data
@TableName("t_user_point_detail")
public class UserPointDetailDO {
    
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
     * 关联订单号
     */
    private String orderSn;
    
    /**
     * 事务ID（TCC全局事务ID）
     */
    private String transactionId;
    
    /**
     * 业务ID（幂等）
     */
    private String businessId;
    
    /**
     * 积分变动值
     */
    private Integer delta;
    
    /**
     * 变动后剩余积分
     */
    private Integer remainder;
    
    /**
     * 状态（TRY/CONFIRM/CANCEL）
     */
    private String state;
    
    /**
     * 积分类型（购票获得、抵扣等）
     */
    private String type;
    
    /**
     * 变动说明
     */
    private String message;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}

