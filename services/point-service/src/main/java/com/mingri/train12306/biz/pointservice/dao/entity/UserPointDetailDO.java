package com.mingri.train12306.biz.pointservice.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户积分变动明细表
 * 对应文档中的SugarDetail设计
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
     * 积分变动值（正数=发放，负数=消耗）
     */
    private Integer delta;
    
    /**
     * 剩余积分（平账计算用，消耗时递减）
     */
    private Integer remainder;
    
    /**
     * 状态（INIT/PENDING/COMMITTED/CANCELED/REVERTED）
     */
    private String state;
    
    /**
     * 积分类型（购票获得、抵扣等）
     */
    private String type;
    
    /**
     * 积分来源（如：ORDER_REWARD, ACTIVITY, REFUND等）
     */
    private String source;
    
    /**
     * 积分类型编码（如：FREEZE,CONSUME等）
     */
    private String code;
    
    /**
     * 标签
     */
    private String tag;
    
    /**
     * 变动说明
     */
    private String message;
    
    /**
     * 扩展信息（JSON格式，存放回退记录等）
     */
    private String ext;
    
    /**
     * 生效时间
     */
    private LocalDateTime effectiveTime;
    
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    
    /**
     * 是否可见（1=可见，0=不可见）
     */
    private Integer visible;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

