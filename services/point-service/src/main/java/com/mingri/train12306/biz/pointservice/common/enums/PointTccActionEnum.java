package com.mingri.train12306.biz.pointservice.common.enums;

/**
 * 积分TCC操作类型枚举
 */
public enum PointTccActionEnum {
    
    /**
     * 冻结积分（购票获得积分，先冻结）
     */
    FREEZE,
    
    /**
     * 消费积分（积分抵扣）
     */
    CONSUME,
    
    /**
     * 发放积分
     */
    GRANT
}

