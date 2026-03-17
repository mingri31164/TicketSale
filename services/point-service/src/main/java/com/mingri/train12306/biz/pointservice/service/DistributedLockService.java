package com.mingri.train12306.biz.pointservice.service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务接口
 * 使用Redis实现分布式锁，保证并发安全
 */
public interface DistributedLockService {
    
    /**
     * 获取锁（带等待时间）
     * 
     * @param key 锁key
     * @param waitTimeInMills 等待时间（毫秒）
     * @param leaseTime 锁租期
     * @param timeUnit 时间单位
     * @return 锁序列号（用于释放锁），获取失败返回null
     */
    String lockWithWaitTime(String key, long waitTimeInMills, long leaseTime, TimeUnit timeUnit);
    
    /**
     * 尝试获取锁
     * 
     * @param key 锁key
     * @param leaseTime 锁租期
     * @param timeUnit 时间单位
     * @return 锁序列号，获取失败返回null
     */
    String tryLock(String key, long leaseTime, TimeUnit timeUnit);
    
    /**
     * 释放锁
     * 
     * @param key 锁key
     * @param sequenceId 锁序列号
     * @return 是否释放成功
     */
    boolean unlock(String key, String sequenceId);
    
    /**
     * 简单加锁（默认10秒租期）
     * 
     * @param key 锁key
     * @return 锁序列号，获取失败返回null
     */
    default String lock(String key) {
        return lockWithWaitTime(key, 0, 10, TimeUnit.SECONDS);
    }
    
    /**
     * 简单加锁（带等待时间）
     * 
     * @param key 锁key
     * @param waitTimeInMills 等待时间
     * @return 锁序列号，获取失败返回null
     */
    default String lockWithWaitTime(String key, long waitTimeInMills) {
        return lockWithWaitTime(key, waitTimeInMills, 10, TimeUnit.SECONDS);
    }
    
    /**
     * 用户积分操作锁
     * 
     * @param userId 用户ID
     * @return 锁序列号
     */
    default String lockUserPoint(Long userId) {
        return lock("point:user:" + userId);
    }
    
    /**
     * 释放用户积分锁
     * 
     * @param userId 用户ID
     * @param sequenceId 锁序列号
     * @return 是否释放成功
     */
    default boolean unlockUserPoint(Long userId, String sequenceId) {
        return unlock("point:user:" + userId, sequenceId);
    }
}
