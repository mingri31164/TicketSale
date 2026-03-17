package com.mingri.train12306.biz.pointservice.service.impl;

import com.mingri.train12306.biz.pointservice.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务实现
 * 基于Redis Lua脚本实现，保证原子性
 * 
 * 锁实现原理：
 * 1. 加锁：Lua脚本原子执行 SETNX + EXPIRE
 * 2. 解锁：Lua脚本原子执行 GET + DEL（防误删）
 * 3. 防误删：只删除自己加的锁
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockServiceImpl implements DistributedLockService {

    private final StringRedisTemplate stringRedisTemplate;

    // Lua脚本：原子性 SETNX + EXPIRE
    private static final String LOCK_SCRIPT = 
            "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
            "  redis.call('expire', KEYS[1], ARGV[2]) " +
            "  return 1 " +
            "else return 0 end";

    // Lua脚本：原子性 GET + DEL（防误删）
    private static final String UNLOCK_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    // 初始化Lua脚本
    private static final DefaultRedisScript<Long> LOCK_REDIS_SCRIPT;
    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT;

    static {
        LOCK_REDIS_SCRIPT = new DefaultRedisScript<>();
        LOCK_REDIS_SCRIPT.setScriptText(LOCK_SCRIPT);
        LOCK_REDIS_SCRIPT.setResultType(Long.class);

        UNLOCK_REDIS_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_REDIS_SCRIPT.setScriptText(UNLOCK_SCRIPT);
        UNLOCK_REDIS_SCRIPT.setResultType(Long.class);
    }

    @Override
    public String lockWithWaitTime(String key, long waitTimeInMills, long leaseTime, TimeUnit timeUnit) {
        String sequenceId = generateSequenceId();
        int expireSeconds = (int) timeUnit.toSeconds(leaseTime);
        
        long startTime = System.currentTimeMillis();
        
        while (true) {
            // 尝试获取锁（原子操作）
            Long acquired = stringRedisTemplate.execute(
                    LOCK_REDIS_SCRIPT,
                    Collections.singletonList(key),
                    sequenceId,
                    String.valueOf(expireSeconds)
            );
            
            if (acquired != null && acquired == 1L) {
                log.debug("[分布式锁] 获取锁成功, key={}, sequenceId={}", key, sequenceId);
                return sequenceId;
            }
            
            // 等待超时
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= waitTimeInMills) {
                log.debug("[分布式锁] 获取锁等待超时, key={}, waitTime={}ms", key, waitTimeInMills);
                return null;
            }
            
            // 短暂等待后重试
            sleep(10);
        }
    }

    @Override
    public String tryLock(String key, long leaseTime, TimeUnit timeUnit) {
        String sequenceId = generateSequenceId();
        int expireSeconds = (int) timeUnit.toSeconds(leaseTime);
        
        Long acquired = stringRedisTemplate.execute(
                LOCK_REDIS_SCRIPT,
                Collections.singletonList(key),
                sequenceId,
                String.valueOf(expireSeconds)
        );
        
        if (acquired != null && acquired == 1L) {
            log.debug("[分布式锁] 尝试获取锁成功, key={}, sequenceId={}", key, sequenceId);
            return sequenceId;
        }
        
        return null;
    }

    @Override
    public boolean unlock(String key, String sequenceId) {
        if (sequenceId == null) {
            return false;
        }
        
        try {
            Long result = stringRedisTemplate.execute(
                    UNLOCK_REDIS_SCRIPT,
                    Collections.singletonList(key),
                    sequenceId
            );
            
            boolean success = result != null && result > 0;
            if (success) {
                log.debug("[分布式锁] 释放锁成功, key={}, sequenceId={}", key, sequenceId);
            } else {
                log.warn("[分布式锁] 释放锁失败，序列号不匹配或锁不存在, key={}, sequenceId={}", key, sequenceId);
            }
            return success;
        } catch (Exception e) {
            log.error("[分布式锁] 释放锁异常, key={}, sequenceId={}", key, sequenceId, e);
            return false;
        }
    }

    /**
     * 生成唯一序列号
     */
    private String generateSequenceId() {
        return String.valueOf(System.nanoTime()) + ":" + Thread.currentThread().getId();
    }

    /**
     * 线程休眠
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
