package com.mingri.train12306.biz.pointservice.service.impl;

import com.mingri.train12306.biz.pointservice.common.enums.PointTccActionEnum;
import com.mingri.train12306.biz.pointservice.common.enums.PointTccStatusEnum;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointTccDO;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointDetailMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointTccMapper;
import com.mingri.train12306.biz.pointservice.service.PointTccService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 积分TCC服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointTccServiceImpl implements PointTccService {

    private final UserPointMapper userPointMapper;
    private final UserPointDetailMapper userPointDetailMapper;
    private final UserPointTccMapper userPointTccMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryFreezePoint(String transactionId, Long userId, String orderSn, Integer point) {
        log.info("[TCC-Try] 冻结积分开始, transactionId={}, userId={}, orderSn={}, point={}", 
                transactionId, userId, orderSn, point);
        
        // 1. 幂等校验
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
        tcc.setStatus(PointTccStatusEnum.TRY.name());
        tcc.setCreateTime(LocalDateTime.now());
        tcc.setUpdateTime(LocalDateTime.now());
        userPointTccMapper.insert(tcc);

        // 4. 记录明细
        UserPointDetailDO detail = new UserPointDetailDO();
        detail.setUserId(userId);
        detail.setOrderSn(orderSn);
        detail.setTransactionId(transactionId);
        detail.setBusinessId(transactionId + "_TRY");
        detail.setDelta(point);
        detail.setRemainder(0); // Try阶段暂不更新余额
        detail.setState(PointTccStatusEnum.TRY.name());
        detail.setType("购票获得");
        detail.setMessage("购票冻结积分");
        detail.setCreateTime(LocalDateTime.now());
        userPointDetailMapper.insert(detail);

        log.info("[TCC-Try] 冻结积分成功, transactionId={}, userId={}, point={}", transactionId, userId, point);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmGrantPoint(String transactionId) {
        log.info("[TCC-Confirm] 确认发放积分开始, transactionId={}", transactionId);
        
        // 1. 查询TCC事务
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Confirm] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        if (PointTccStatusEnum.CONFIRM.name().equals(tcc.getStatus())) {
            log.warn("[TCC-Confirm] TCC事务已Confirm, transactionId={}", transactionId);
            return true;
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
        tcc.setStatus(PointTccStatusEnum.CONFIRM.name());
        tcc.setUpdateTime(LocalDateTime.now());
        userPointTccMapper.updateById(tcc);

        // 4. 记录明细
        UserPointDO userPoint = userPointMapper.selectByUserId(tcc.getUserId());
        UserPointDetailDO detail = new UserPointDetailDO();
        detail.setUserId(tcc.getUserId());
        detail.setOrderSn(tcc.getOrderSn());
        detail.setTransactionId(transactionId);
        detail.setBusinessId(transactionId + "_CONFIRM");
        detail.setDelta(tcc.getPoint());
        detail.setRemainder(userPoint != null ? userPoint.getPoint() : 0);
        detail.setState(PointTccStatusEnum.CONFIRM.name());
        detail.setType("购票获得");
        detail.setMessage("购票获得积分");
        detail.setCreateTime(LocalDateTime.now());
        userPointDetailMapper.insert(detail);

        log.info("[TCC-Confirm] 确认发放积分成功, transactionId={}, userId={}, point={}", 
                transactionId, tcc.getUserId(), tcc.getPoint());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelFreezePoint(String transactionId) {
        log.info("[TCC-Cancel] 取消冻结积分开始, transactionId={}", transactionId);
        
        // 1. 查询TCC事务
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Cancel] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        if (PointTccStatusEnum.CANCEL.name().equals(tcc.getStatus())) {
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
        tcc.setStatus(PointTccStatusEnum.CANCEL.name());
        tcc.setUpdateTime(LocalDateTime.now());
        userPointTccMapper.updateById(tcc);

        // 4. 记录明细
        UserPointDetailDO detail = new UserPointDetailDO();
        detail.setUserId(tcc.getUserId());
        detail.setOrderSn(tcc.getOrderSn());
        detail.setTransactionId(transactionId);
        detail.setBusinessId(transactionId + "_CANCEL");
        detail.setDelta(-tcc.getPoint());
        detail.setRemainder(0);
        detail.setState(PointTccStatusEnum.CANCEL.name());
        detail.setType("购票取消");
        detail.setMessage("取消购票，归还冻结积分");
        detail.setCreateTime(LocalDateTime.now());
        userPointDetailMapper.insert(detail);

        log.info("[TCC-Cancel] 取消冻结积分成功, transactionId={}, userId={}, point={}", 
                transactionId, tcc.getUserId(), tcc.getPoint());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryConsumePoint(String transactionId, Long userId, String orderSn, Integer point) {
        log.info("[TCC-Try] 冻结并扣减积分开始, transactionId={}, userId={}, orderSn={}, point={}", 
                transactionId, userId, orderSn, point);
        
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
        tcc.setStatus(PointTccStatusEnum.TRY.name());
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
        detail.setState(PointTccStatusEnum.TRY.name());
        detail.setType("积分抵扣");
        detail.setMessage("积分抵扣冻结");
        detail.setCreateTime(LocalDateTime.now());
        userPointDetailMapper.insert(detail);

        log.info("[TCC-Try] 冻结并扣减积分成功, transactionId={}, userId={}, point={}", transactionId, userId, point);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmConsumePoint(String transactionId) {
        log.info("[TCC-Confirm] 确认扣减积分开始, transactionId={}", transactionId);
        
        // 1. 查询TCC事务
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Confirm] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        if (PointTccStatusEnum.CONFIRM.name().equals(tcc.getStatus())) {
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
        tcc.setStatus(PointTccStatusEnum.CONFIRM.name());
        tcc.setUpdateTime(LocalDateTime.now());
        userPointTccMapper.updateById(tcc);

        // 4. 记录明细
        UserPointDO userPoint = userPointMapper.selectByUserId(tcc.getUserId());
        UserPointDetailDO detail = new UserPointDetailDO();
        detail.setUserId(tcc.getUserId());
        detail.setOrderSn(tcc.getOrderSn());
        detail.setTransactionId(transactionId);
        detail.setBusinessId(transactionId + "_CONFIRM");
        detail.setDelta(-tcc.getPoint());
        detail.setRemainder(userPoint != null ? userPoint.getPoint() : 0);
        detail.setState(PointTccStatusEnum.CONFIRM.name());
        detail.setType("积分抵扣");
        detail.setMessage("积分抵扣成功");
        detail.setCreateTime(LocalDateTime.now());
        userPointDetailMapper.insert(detail);

        log.info("[TCC-Confirm] 确认扣减积分成功, transactionId={}, userId={}, point={}", 
                transactionId, tcc.getUserId(), tcc.getPoint());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelConsumePoint(String transactionId) {
        log.info("[TCC-Cancel] 归还扣减积分开始, transactionId={}", transactionId);
        
        // 1. 查询TCC事务
        UserPointTccDO tcc = userPointTccMapper.selectByTransactionId(transactionId);
        if (tcc == null) {
            log.error("[TCC-Cancel] TCC事务不存在, transactionId={}", transactionId);
            return false;
        }
        if (PointTccStatusEnum.CANCEL.name().equals(tcc.getStatus())) {
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
        tcc.setStatus(PointTccStatusEnum.CANCEL.name());
        tcc.setUpdateTime(LocalDateTime.now());
        userPointTccMapper.updateById(tcc);

        // 4. 记录明细
        UserPointDO userPoint = userPointMapper.selectByUserId(tcc.getUserId());
        UserPointDetailDO detail = new UserPointDetailDO();
        detail.setUserId(tcc.getUserId());
        detail.setOrderSn(tcc.getOrderSn());
        detail.setTransactionId(transactionId);
        detail.setBusinessId(transactionId + "_CANCEL");
        detail.setDelta(tcc.getPoint());
        detail.setRemainder(userPoint != null ? userPoint.getPoint() : 0);
        detail.setState(PointTccStatusEnum.CANCEL.name());
        detail.setType("积分归还");
        detail.setMessage("支付失败，归还抵扣积分");
        detail.setCreateTime(LocalDateTime.now());
        userPointDetailMapper.insert(detail);

        log.info("[TCC-Cancel] 归还扣减积分成功, transactionId={}, userId={}, point={}", 
                transactionId, tcc.getUserId(), tcc.getPoint());
        return true;
    }
}

