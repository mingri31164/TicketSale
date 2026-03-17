package com.mingri.train12306.biz.pointservice.task;

import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDO;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 积分一致性对账任务（T+1对账）
 * 每日凌晨执行，校验用户总积分与明细积分的一致性
 * 
 * 对账逻辑：
 * - detailTotal = sum(remainder) where state=COMMITTED
 * - gap = detailTotal - totalPoint
 * - gap > 0: 总积分 < 明细（数据异常，告警）
 * - gap < 0: 总积分 > 明细（自动修复）
 * - gap = 0: 一致
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "point.reconcile.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PointConsistencyCheckTask {

    private final UserPointMapper userPointMapper;
    private final UserPointDetailMapper userPointDetailMapper;

    private static final int BATCH_QUERY_SIZE = 500;

    /**
     * 每日凌晨2点执行对账任务
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkPointConsistency() {
        log.info("[T+1对账] 开始执行积分一致性校验任务");
        long startTime = System.currentTimeMillis();
        
        int totalCheckCount = 0;
        int inconsistentCount = 0;
        int fixedCount = 0;
        
        try {
            // 批量查询所有用户积分账户
            long lastId = 0;
            while (true) {
                List<UserPointDO> userPointList = userPointMapper.queryBatch(BATCH_QUERY_SIZE, lastId);
                
                if (userPointList == null || userPointList.isEmpty()) {
                    break;
                }
                
                for (UserPointDO userPoint : userPointList) {
                    try {
                        totalCheckCount++;
                        ConsistencyResult result = checkUserConsistency(userPoint);
                        
                        if (!result.isConsistent()) {
                            inconsistentCount++;
                            log.warn("[T+1对账] 用户积分不一致, userId={}, totalPoint={}, detailTotal={}, gap={}", 
                                    userPoint.getUserId(), userPoint.getPoint(), result.getDetailTotal(), result.getGap());
                            
                            // 自动修复：总积分 > 明细时扣减
                            if (result.getGap() < 0) {
                                int fixAmount = Math.abs(result.getGap());
                                userPointMapper.decreasePoint(userPoint.getUserId(), fixAmount);
                                fixedCount++;
                                log.info("[T+1对账] 自动修复成功, userId={}, 修复金额={}", userPoint.getUserId(), fixAmount);
                            }
                        }
                    } catch (Exception e) {
                        log.error("[T+1对账] 检查用户积分异常, userId={}", userPoint.getUserId(), e);
                    }
                }
                
                lastId = userPointList.get(userPointList.size() - 1).getId();
                
                if (userPointList.size() < BATCH_QUERY_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("[T+1对账] 执行对账任务异常", e);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[T+1对账] 对账任务完成, 耗时={}ms, 检查用户数={}, 不一致数={}, 修复数={}", 
                elapsed, totalCheckCount, inconsistentCount, fixedCount);
    }

    /**
     * 检查单个用户积分一致性
     */
    private ConsistencyResult checkUserConsistency(UserPointDO userPoint) {
        // 查询用户明细积分 remainder 加和（只统计已提交的）
        Integer detailTotal = userPointDetailMapper.queryTotalCanConsume(userPoint.getUserId());
        
        // 计算差值
        long gap = (detailTotal != null ? detailTotal : 0) - (userPoint.getPoint() != null ? userPoint.getPoint() : 0);
        
        return new ConsistencyResult(detailTotal != null ? detailTotal : 0, (int) gap, gap == 0);
    }

    /**
     * 一致性检查结果
     */
    private static class ConsistencyResult {
        private final int detailTotal;
        private final int gap;
        private final boolean consistent;

        public ConsistencyResult(int detailTotal, int gap, boolean consistent) {
            this.detailTotal = detailTotal;
            this.gap = gap;
            this.consistent = consistent;
        }

        public int getDetailTotal() {
            return detailTotal;
        }

        public int getGap() {
            return gap;
        }

        public boolean isConsistent() {
            return consistent;
        }
    }
}
