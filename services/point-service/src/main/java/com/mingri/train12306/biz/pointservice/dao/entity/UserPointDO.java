package com.mingri.train12306.biz.pointservice.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户积分账户表
 */
@Data
@TableName("t_user_point")
public class UserPointDO {
    
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
     * 可用积分
     */
    private Integer point;
    
    /**
     * 冻结积分
     */
    private Integer frozenPoint;
    
    /**
     * 累计获得积分
     */
    private Integer totalPoint;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

