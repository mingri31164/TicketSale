package com.mingri.train12306.biz.pointservice.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 积分服务监控指标
 * 用于记录业务操作指标，便于监控和告警
 */
@Slf4j
@Component
public class PointMonitor {

    /**
     * 积分一致性检查
     */
    public static final String POINT_CONSISTENT = "point.consistent";
    
    /**
     * 积分不一致类型：SUGAR_LESS（明细多于总积分）
     */
    public static final String TYPE_POINT_LESS = "POINT_LESS";
    
    /**
     * 积分不一致类型：POINT_MORE（总积分多于明细）
     */
    public static final String TYPE_POINT_MORE = "POINT_MORE";
    
    /**
     * 平账失败
     */
    public static final String RECONCILE_SETTLEMENT_ERROR = "reconcile.settlement.error";
    
    /**
     * 积分回退失败
     */
    public static final String POINT_REVERT_ERROR = "point.revert.error";
    
    /**
     * 积分消耗失败
     */
    public static final String POINT_CONSUME_ERROR = "point.consume.error";
    
    /**
     * 分布式锁获取失败
     */
    public static final String LOCK_FAIL = "lock.fail";
    
    /**
     * 记录积分一致性检查结果
     */
    public void recordConsistency(boolean consistent, String type) {
        if (!consistent) {
            log.warn("[监控] 积分不一致, type={}", type);
        }
    }
    
    /**
     * 记录平账错误
     */
    public void recordReconcileError(String message) {
        log.error("[监控] 平账失败: {}", message);
    }
    
    /**
     * 记录积分回退错误
     */
    public void recordRevertError(String message) {
        log.error("[监控] 积分回退失败: {}", message);
    }
    
    /**
     * 记录积分消耗错误
     */
    public void recordConsumeError(String message) {
        log.error("[监控] 积分消耗失败: {}", message);
    }
    
    /**
     * 记录锁获取失败
     */
    public void recordLockFail(String key) {
        log.warn("[监控] 获取分布式锁失败, key={}", key);
    }
}
