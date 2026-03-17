package com.mingri.train12306.biz.pointservice.service.impl;

import com.mingri.train12306.biz.pointservice.common.enums.PointTccActionEnum;
import com.mingri.train12306.biz.pointservice.common.enums.PointTccStatusEnum;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointTccDO;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointDetailMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointTccMapper;
import com.mingri.train12306.biz.pointservice.service.DistributedLockService;
import com.mingri.train12306.biz.pointservice.service.PointTccService;
import com.mingri.train12306.biz.pointservice.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分TCC服务实现
 * 优化版本：集成分布式锁、平账服务，增强事务状态和幂等性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointTccServiceImpl implements PointTccService {

    private final UserPointMapper userPointMapper;
    private final UserPointDetailMapper userPointDetailMapper;
    private final UserPointTccMapper userPointTccMapper;
    private final DistributedLockService distributedLockService;
    private final ReconcileService reconcileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryFreezePoint(String transactionId, Long userId, String orderSn, Integer point) {
        log.info("[TCC-Try] 冻结积分开始, transactionId={}, userId={}, orderSn={}, point={}", 
                transactionId, userId, orderSn, point);
        
        // 0. 获取分布式锁
        String lockId = distributedLockService.lockUserPoint(userId);
        if (lockId == null) {
            log.error("[TCC-Try] 获取分布式锁失败, userId={}", userId);
            throw new RuntimeException("获取分布式锁失败");
        }
        
        try {
            // 1. 幂等校验（基于businessId）
            UserPointTccDO existTcc = userPointTccMapper.selectByTransactionId(transactionId);
            if (existTcc != null) {
                log.warn("[TCC-Try] TCC事务已存在，transactionId={}", transactionId);
                return true;
            }

            // 2. 查询或初始化用户积分账户
            UserPointDO userPoint = userPointMapper.selectByUserId(userId);
            if (userPoint == null) {
                userPoint = new UserPointDO();
                userPoint.setUserId(userId);
                userPoint.setPoint(0);
                userPoint.setFrozenPoint(point);
                userPoint.setTotalPoint(0);
                userPoint.setCreateTime(LocalDateTime.now());
                userPoint.setUpdateTime(LocalDateTime.now());
                userPointMapper.insert(userPoint);
                log.info("[TCC-Try] 初始化用户积分账户, userId={}", userId);
            } else {
                // 增加冻结积分
                int rows = userPointMapper.increaseFrozenPoint(userId, point);
                if (rows <= 0) {
                    log.error("[TCC-Try] 增加冻结积分失败, userId={}, point={}", userId, point);
                    throw new RuntimeException("增加冻结积分失败");
                }
            }

            // 3. 记录TCC事务
            UserPointTccDO tcc = new UserPointTccDO();
            tcc.setTransactionId(transactionId);
            tcc.setUserId(userId);
            tcc.setOrderSn(orderSn);
            tcc.setPoint(point);
            tcc.setAction(PointTccActionEnum.FREEZE.name());
            tcc.setStatus(PointTccStatusEnum.PENDING.name());
            tcc.setCreateTime(LocalDateTime.now());
            tcc.setUpdateTime(LocalDateTime.now());
            userPointTccMapper.insert(tcc);

            // 4. 记录明细（状态为PENDING）
            UserPointDetailDO detail = new UserPointDetailDO();
            detail.setUserId(userId);
            detail.setOrderSn(orderSn);
            detail.setTransactionId(transactionId);
            detail.setBusinessId(transactionId + "_TRY");
            detail.setDelta(point);
            detail.setRemainder(0);
            detail.setState(PointTccStatusEnum.PENDING.name());
            detail.setType("购票获得");
            detail.setSource("ORDER_REWARD");
            detail.setCode("FREEZE");
            detail.setMessage("购票冻结积分");
            detail.setCreateTime(LocalDateTime.now());
            userPointDetailMapper.insert(detail);

            log.info("[TCC-Try] 冻结积分成功, transactionId={}, userId={}, point={}", transactionId, userId, point);
            return true;
        } finally {
            // 5. 释放分布式锁
            distributedLockService.unlockUserPoint(userId, lockId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmGrantPoint(String transactionId) {
        log.info("[TCC-Confirm] 确认发放积分开始, transactionId={}", transactionId);
        
        // 获取分布式锁
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Confirm] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        
        String lockId = distributedLockService.lockUserPoint(tcc.getUserId());
        if (lockId == null) {
            log.error("[TCC-Confirm] 获取分布式锁失败, userId={}", tcc.getUserId());
            throw new RuntimeException("获取分布式锁失败");
        }
        
        try {
            // 1. 幂等校验（基于状态）
            if (PointTccStatusEnum.COMMITTED.name().equals(tcc.getStatus())) {
                log.warn("[TCC-Confirm] TCC事务已Confirm, transactionId={}", transactionId);
                return true;
            }
            
            if (!PointTccStatusEnum.PENDING.name().equals(tcc.getStatus())) {
                log.error("[TCC-Confirm] TCC事务状态不正确, transactionId={}, status={}", transactionId, tcc.getStatus());
                throw new RuntimeException("TCC事务状态不正确");
            }

            // 2. 扣减冻结积分，增加可用积分
            int rows1 = userPointMapper.decreaseFrozenPoint(tcc.getUserId(), tcc.getPoint());
            if (rows1 <= 0) {
                log.error("[TCC-Confirm] 扣减冻结积分失败, userId={}, point={}", tcc.getUserId(), tcc.getPoint());
                throw new RuntimeException("扣减冻结积分失败");
            }
            
            int rows2 = userPointMapper.increasePoint(tcc.getUserId(), tcc.getPoint());
            if (rows2 <= 0) {
                log.error("[TCC-Confirm] 增加可用积分失败, userId={}, point={}", tcc.getUserId(), tcc.getPoint());
                throw new RuntimeException("增加可用积分失败");
            }
            
            userPointMapper.increaseTotalPoint(tcc.getUserId(), tcc.getPoint());

            // 3. 更新TCC状态
            tcc.setStatus(PointTccStatusEnum.COMMITTED.name());
            tcc.setUpdateTime(LocalDateTime.now());
            userPointTccMapper.updateById(tcc);

            // 4. 更新明细状态为COMMITTED，并计算过期时间
            UserPointDetailDO detail = userPointDetailMapper.selectByTransactionId(transactionId);
            if (detail != null) {
                detail.setState(PointTccStatusEnum.COMMITTED.name());
                detail.setRemainder(tcc.getPoint());
                detail.setEffectiveTime(LocalDateTime.now());
                // 默认有效期1年
                detail.setExpireTime(LocalDateTime.now().plusYears(1));
                detail.setUpdateTime(LocalDateTime.now());
                userPointDetailMapper.updateById(detail);
                
                // 5. 触发平账（检查是否有未平账务）
                try {
                    reconcileService.reconcileSettlement(List.of(detail));
                } catch (Exception e) {
                    log.error("[TCC-Confirm] 平账失败，不影响主流程, transactionId={}", transactionId, e);
                }
            }

            log.info("[TCC-Confirm] 确认发放积分成功, transactionId={}, userId={}, point={}", 
                    transactionId, tcc.getUserId(), tcc.getPoint());
            return true;
        } finally {
            distributedLockService.unlockUserPoint(tcc.getUserId(), lockId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelFreezePoint(String transactionId) {
        log.info("[TCC-Cancel] 取消冻结积分开始, transactionId={}", transactionId);
        
        // 获取分布式锁
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Cancel] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        
        String lockId = distributedLockService.lockUserPoint(tcc.getUserId());
        if (lockId == null) {
            log.error("[TCC-Cancel] 获取分布式锁失败, userId={}", tcc.getUserId());
            throw new RuntimeException("获取分布式锁失败");
        }
        
        try {
            // 1. 幂等校验
            if (PointTccStatusEnum.CANCELED.name().equals(tcc.getStatus())) {
                log.warn("[TCC-Cancel] TCC事务已Cancel, transactionId={}", transactionId);
                return true;
            }

            // 2. 扣减冻结积分（归还）
            int rows = userPointMapper.decreaseFrozenPoint(tcc.getUserId(), tcc.getPoint());
            if (rows <= 0) {
                log.error("[TCC-Cancel] 扣减冻结积分失败, userId={}, point={}", tcc.getUserId(), tcc.getPoint());
                throw new RuntimeException("扣减冻结积分失败");
            }

            // 3. 更新TCC状态
            tcc.setStatus(PointTccStatusEnum.CANCELED.name());
            tcc.setUpdateTime(LocalDateTime.now());
            userPointTccMapper.updateById(tcc);

            // 4. 更新明细状态为CANCELED
            UserPointDetailDO detail = userPointDetailMapper.selectByTransactionId(transactionId);
            if (detail != null) {
                detail.setState(PointTccStatusEnum.CANCELED.name());
                detail.setUpdateTime(LocalDateTime.now());
                userPointDetailMapper.updateById(detail);
            }

            log.info("[TCC-Cancel] 取消冻结积分成功, transactionId={}, userId={}, point={}", 
                    transactionId, tcc.getUserId(), tcc.getPoint());
            return true;
        } finally {
            distributedLockService.unlockUserPoint(tcc.getUserId(), lockId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryConsumePoint(String transactionId, Long userId, String orderSn, Integer point) {
        log.info("[TCC-Try] 冻结并扣减积分开始, transactionId={}, userId={}, orderSn={}, point={}", 
                transactionId, userId, orderSn, point);
        
        // 0. 获取分布式锁
        String lockId = distributedLockService.lockUserPoint(userId);
        if (lockId == null) {
            log.error("[TCC-Try] 获取分布式锁失败, userId={}", userId);
            throw new RuntimeException("获取分布式锁失败");
        }
        
        try {
            // 1. 幂等校验
            UserPointTccDO existTcc = userPointTccMapper.selectByTransactionId(transactionId);
            if (existTcc != null) {
                log.warn("[TCC-Try] TCC事务已存在, transactionId={}", transactionId);
                return true;
            }

            // 2. 检查可用积分是否足够
            UserPointDO userPoint = userPointMapper.selectByUserId(userId);
            if (userPoint == null || userPoint.getPoint() < point) {
                log.error("[TCC-Try] 积分不足, userId={}, 需要={}, 可用={}", 
                        userId, point, userPoint == null ? 0 : userPoint.getPoint());
                throw new RuntimeException("积分不足");
            }

            // 3. 冻结积分（从可用积分转移到冻结积分）
            int rows1 = userPointMapper.decreasePoint(userId, point);
            if (rows1 <= 0) {
                log.error("[TCC-Try] 扣减可用积分失败, userId={}, point={}", userId, point);
                throw new RuntimeException("扣减可用积分失败");
            }
            
            int rows2 = userPointMapper.increaseFrozenPoint(userId, point);
            if (rows2 <= 0) {
                log.error("[TCC-Try] 增加冻结积分失败, userId={}, point={}", userId, point);
                throw new RuntimeException("增加冻结积分失败");
            }

            // 4. 记录TCC事务
            UserPointTccDO tcc = new UserPointTccDO();
            tcc.setTransactionId(transactionId);
            tcc.setUserId(userId);
            tcc.setOrderSn(orderSn);
            tcc.setPoint(point);
            tcc.setAction(PointTccActionEnum.CONSUME.name());
            tcc.setStatus(PointTccStatusEnum.PENDING.name());
            tcc.setCreateTime(LocalDateTime.now());
            tcc.setUpdateTime(LocalDateTime.now());
            userPointTccMapper.insert(tcc);

            // 5. 记录明细
            UserPointDetailDO detail = new UserPointDetailDO();
            detail.setUserId(userId);
            detail.setOrderSn(orderSn);
            detail.setTransactionId(transactionId);
            detail.setBusinessId(transactionId + "_TRY");
            detail.setDelta(-point);
            detail.setRemainder(0);
            detail.setState(PointTccStatusEnum.PENDING.name());
            detail.setType("积分抵扣");
            detail.setSource("ORDER_CONSUME");
            detail.setCode("CONSUME");
            detail.setMessage("积分抵扣冻结");
            detail.setCreateTime(LocalDateTime.now());
            userPointDetailMapper.insert(detail);

            log.info("[TCC-Try] 冻结并扣减积分成功, transactionId={}, userId={}, point={}", transactionId, userId, point);
            return true;
        } finally {
            distributedLockService.unlockUserPoint(userId, lockId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmConsumePoint(String transactionId) {
        log.info("[TCC-Confirm] 确认扣减积分开始, transactionId={}", transactionId);
        
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Confirm] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        
        String lockId = distributedLockService.lockUserPoint(tcc.getUserId());
        if (lockId == null) {
            log.error("[TCC-Confirm] 获取分布式锁失败, userId={}", tcc.getUserId());
            throw new RuntimeException("获取分布式锁失败");
        }
        
        try {
            // 1. 幂等校验
            if (PointTccStatusEnum.COMMITTED.name().equals(tcc.getStatus())) {
                log.warn("[TCC-Confirm] TCC事务已Confirm, transactionId={}", transactionId);
                return true;
            }

            // 2. 扣减冻结积分（正式扣减）
            int rows = userPointMapper.decreaseFrozenPoint(tcc.getUserId(), tcc.getPoint());
            if (rows <= 0) {
                log.error("[TCC-Confirm] 扣减冻结积分失败, userId={}, point={}", tcc.getUserId(), tcc.getPoint());
                throw new RuntimeException("扣减冻结积分失败");
            }

            // 3. 更新TCC状态
            tcc.setStatus(PointTccStatusEnum.COMMITTED.name());
            tcc.setUpdateTime(LocalDateTime.now());
            userPointTccMapper.updateById(tcc);

            // 4. 更新明细状态
            UserPointDetailDO detail = userPointDetailMapper.selectByTransactionId(transactionId);
            if (detail != null) {
                UserPointDO userPoint = userPointMapper.selectByUserId(tcc.getUserId());
                detail.setState(PointTccStatusEnum.COMMITTED.name());
                detail.setRemainder(-tcc.getPoint());
                detail.setUpdateTime(LocalDateTime.now());
                userPointDetailMapper.updateById(detail);
            }

            log.info("[TCC-Confirm] 确认扣减积分成功, transactionId={}, userId={}, point={}", 
                    transactionId, tcc.getUserId(), tcc.getPoint());
            return true;
        } finally {
            distributedLockService.unlockUserPoint(tcc.getUserId(), lockId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelConsumePoint(String transactionId) {
        log.info("[TCC-Cancel] 归还扣减积分开始, transactionId={}", transactionId);
        
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Cancel] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        
        String lockId = distributedLockService.lockUserPoint(tcc.getUserId());
        if (lockId == null) {
            log.error("[TCC-Cancel] 获取分布式锁失败, userId={}", tcc.getUserId());
            throw new RuntimeException("获取分布式锁失败");
        }
        
        try {
            // 1. 幂等校验
            if (PointTccStatusEnum.CANCELED.name().equals(tcc.getStatus())) {
                log.warn("[TCC-Cancel] TCC事务已Cancel, transactionId={}", transactionId);
                return true;
            }

            // 2. 归还积分（从冻结转回可用）
            int rows1 = userPointMapper.decreaseFrozenPoint(tcc.getUserId(), tcc.getPoint());
            if (rows1 <= 0) {
                log.error("[TCC-Cancel] 扣减冻结积分失败, userId={}, point={}", tcc.getUserId(), tcc.getPoint());
                throw new RuntimeException("扣减冻结积分失败");
            }
            
            int rows2 = userPointMapper.increasePoint(tcc.getUserId(), tcc.getPoint());
            if (rows2 <= 0) {
                log.error("[TCC-Cancel] 增加可用积分失败, userId={}, point={}", tcc.getUserId(), tcc.getPoint());
                throw new RuntimeException("增加可用积分失败");
            }

            // 3. 更新TCC状态
            tcc.setStatus(PointTccStatusEnum.CANCELED.name());
            tcc.setUpdateTime(LocalDateTime.now());
            userPointTccMapper.updateById(tcc);

            // 4. 更新明细状态
            UserPointDetailDO detail = userPointDetailMapper.selectByTransactionId(transactionId);
            if (detail != null) {
                detail.setState(PointTccStatusEnum.CANCELED.name());
                detail.setUpdateTime(LocalDateTime.now());
                userPointDetailMapper.updateById(detail);
            }

            log.info("[TCC-Cancel] 归还扣减积分成功, transactionId={}, userId={}, point={}", 
                    transactionId, tcc.getUserId(), tcc.getPoint());
            return true;
        } finally {
            distributedLockService.unlockUserPoint(tcc.getUserId(), lockId);
        }
    }
}

