package com.mingri.train12306.biz.pointservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingri.train12306.biz.pointservice.common.enums.PointTccStatusEnum;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointSettlementDO;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointDetailMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointSettlementMapper;
import com.mingri.train12306.biz.pointservice.service.PointRevertService;
import com.mingri.train12306.biz.pointservice.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 积分回退服务实现
 * 支持全额回退、部分回退、多次回退
 * 
 * 回退类型：
 * 1. 全额回退：全部回退（如订单全额退款）
 * 2. 部分回退：部分回退（如订单部分退款）
 * 3. 多次回退：累计计算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointRevertServiceImpl implements PointRevertService {

    private final UserPointMapper userPointMapper;
    private final UserPointDetailMapper userPointDetailMapper;
    private final UserPointSettlementMapper userPointSettlementMapper;
    private final ReconcileService reconcileService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revertPoint(Long userId, String orderSn, Integer revertPoint) {
        log.info("[积分回退] 开始回退积分, userId={}, orderSn={}, revertPoint={}", userId, orderSn, revertPoint);
        
        // 1. 查询原扣减记录
        List<UserPointSettlementDO> settlements = userPointSettlementMapper.selectByConsumerBusinessId(userId, orderSn + "_consume");
        
        if (settlements == null || settlements.isEmpty()) {
            log.warn("[积分回退] 没有找到原扣减记录, userId={}, orderSn={}", userId, orderSn);
            return false;
        }
        
        // 2. 幂等校验：检查是否已回退过
        UserPointDetailDO existRevert = userPointDetailMapper.selectByBusinessId(orderSn + "_revert");
        if (existRevert != null && PointTccStatusEnum.REVERTED.name().equals(existRevert.getState())) {
            log.warn("[积分回退] 积分已回退过, userId={}, orderSn={}", userId, orderSn);
            return true;
        }
        
        // 3. 计算可回退积分上限
        int totalConsumed = settlements.stream()
                .mapToInt(s -> s.getConsumePoint() != null ? s.getConsumePoint() : 0)
                .sum();
        
        int alreadyReverted = getAlreadyRevertedCount(orderSn);
        
        if (alreadyReverted + revertPoint > totalConsumed) {
            log.error("[积分回退] 回退积分超过可退上限, userId={}, orderSn={}, 可退={}, 尝试退={}", 
                    userId, orderSn, totalConsumed - alreadyReverted, revertPoint);
            throw new RuntimeException("回退积分超过可退上限");
        }
        
        // 4. 查询原扣减对应的settlement，更新发放方remainder
        List<UserPointDetailDO> revertRemainderDetails = new ArrayList<>();
        List<UserPointSettlementDO> revertSettlements = new ArrayList<>();
        
        for (UserPointSettlementDO settlement : settlements) {
            // 查询对应的发放明细
            UserPointDetailDO providerDetail = userPointDetailMapper.selectByBusinessId(settlement.getProviderBusinessId());
            if (providerDetail == null) {
                continue;
            }
            
            // 计算本次回退的积分数量（按比例）
            int revertThisTime = (int) ((double) revertPoint / totalConsumed * settlement.getConsumePoint());
            
            // 更新发放明细的remainder
            Integer currentRemainder = providerDetail.getRemainder() != null ? providerDetail.getRemainder() : 0;
            providerDetail.setRemainder(currentRemainder + revertThisTime);
            revertRemainderDetails.add(providerDetail);
            
            // 生成反向settlement（consumePoint取反）
            UserPointSettlementDO revertSettlement = new UserPointSettlementDO();
            revertSettlement.setUserId(userId);
            revertSettlement.setConsumerSource(settlement.getConsumerSource());
            revertSettlement.setConsumerCode(settlement.getConsumerCode());
            revertSettlement.setProviderSource(settlement.getProviderSource());
            revertSettlement.setProviderCode(settlement.getProviderCode());
            revertSettlement.setConsumerTransactionId(settlement.getConsumerTransactionId());
            revertSettlement.setConsumePoint(-revertThisTime); // 取反
            revertSettlement.setProviderBusinessId(settlement.getProviderBusinessId());
            revertSettlement.setConsumerBusinessId(orderSn + "_revert");
            revertSettlement.setCreateTime(LocalDateTime.now());
            revertSettlement.setUpdateTime(LocalDateTime.now());
            revertSettlements.add(revertSettlement);
        }
        
        // 5. 记录回退明细
        UserPointDetailDO revertDetail = new UserPointDetailDO();
        revertDetail.setUserId(userId);
        revertDetail.setOrderSn(orderSn);
        revertDetail.setTransactionId(UUID.randomUUID().toString());
        revertDetail.setBusinessId(orderSn + "_revert");
        revertDetail.setDelta(revertPoint);
        revertDetail.setRemainder(revertPoint);
        revertDetail.setState(PointTccStatusEnum.REVERTED.name());
        revertDetail.setType("积分回退");
        revertDetail.setSource("REFUND");
        revertDetail.setCode("REVERT");
        revertDetail.setMessage("订单退款，积分回退");
        
        // 记录回退历史
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("revertList", Arrays.asList(orderSn + "_revert"));
        try {
            revertDetail.setExt(objectMapper.writeValueAsString(extMap));
        } catch (JsonProcessingException e) {
            log.error("[积分回退] 序列化ext失败", e);
        }
        revertDetail.setCreateTime(LocalDateTime.now());
        userPointDetailMapper.insert(revertDetail);
        
        // 6. 更新发放明细的remainder
        for (UserPointDetailDO detail : revertRemainderDetails) {
            userPointDetailMapper.updateById(detail);
        }
        
        // 7. 保存回退settlement
        if (!revertSettlements.isEmpty()) {
            userPointSettlementMapper.batchInsert(revertSettlements);
        }
        
        // 8. 更新总积分
        userPointMapper.increasePoint(userId, revertPoint);
        
        // 9. 触发平账
        try {
            List<UserPointDetailDO> revertDetails = new ArrayList<>();
            revertDetail.setRemainder(revertPoint);
            revertDetails.add(revertDetail);
            reconcileService.reconcileSettlement(revertDetails);
        } catch (Exception e) {
            log.error("[积分回退] 平账失败，不影响主流程, userId={}", userId, e);
        }
        
        log.info("[积分回退] 回退积分成功, userId={}, orderSn={}, revertPoint={}", userId, orderSn, revertPoint);
        return true;
    }

    /**
     * 获取已回退积分数量
     */
    private int getAlreadyRevertedCount(String orderSn) {
        UserPointDetailDO revertDetail = userPointDetailMapper.selectByBusinessId(orderSn + "_revert");
        if (revertDetail == null) {
            return 0;
        }
        return revertDetail.getDelta() != null ? revertDetail.getDelta() : 0;
    }

    @Override
    public List<UserPointSettlementDO> queryConsumedOrders(Long userId) {
        // 查询所有消费记录
        return userPointSettlementMapper.selectByConsumerTransactionId(userId, "%");
    }
}
