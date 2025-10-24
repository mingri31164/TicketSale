package com.mingri.train12306.biz.pointservice.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分TCC事务记录表
 */
@Data
@TableName("t_user_point_tcc")
public class UserPointTccDO {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * TCC全局事务ID
     */
    private String transactionId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 关联订单号
     */
    private String orderSn;
    
    /**
     * 涉及积分数
     */
    private Integer point;
    
    /**
     * 操作类型（FREEZE/CONSUME/GRANT）
     */
    private String action;
    
    /**
     * TCC状态（TRY/CONFIRM/CANCEL）
     */
    private String status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

