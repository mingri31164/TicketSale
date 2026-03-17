package com.mingri.train12306.biz.pointservice.common.enums;

/**
 * 积分事务状态枚举
 * 对应文档中的TransactionState设计
 */
public enum PointTccStatusEnum {
    
    /**
     * 初始状态
     */
    INIT(0),
    
    /**
     * 待确认（冻结阶段）
     */
    PENDING(1),
    
    /**
     * 已提交（生效）
     */
    COMMITTED(2),
    
    /**
     * 已取消
     */
    CANCELED(3),
    
    /**
     * 已回退
     */
    REVERTED(4);
    
    private final int code;
    
    PointTccStatusEnum(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
    
    /**
     * 根据code获取枚举
     */
    public static PointTccStatusEnum getByCode(int code) {
        for (PointTccStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}

