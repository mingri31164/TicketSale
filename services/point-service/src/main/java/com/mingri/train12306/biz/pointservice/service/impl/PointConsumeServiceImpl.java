package com.mingri.train12306.biz.pointservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.mingri.train12306.biz.pointservice.common.enums.PointTccStatusEnum;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointSettlementDO;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointDetailMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointSettlementMapper;
import com.mingri.train12306.biz.pointservice.service.PointConsumeService;
import com.mingri.train12306.biz.pointservice.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 积分消耗服务实现
 * 实现文档中的双向指针匹配算法（贪心策略）
 * 
 * 算法特点：
 * 1. 按过期时间排序：优先消耗即将过期的积分
 * 2. 贪心策略：每次取最小值，确保公平
 * 3. O(n+m)复杂度：线性时间复杂度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointConsumeServiceImpl implements PointConsumeService {

    private final UserPointMapper userPointMapper;
    private final UserPointDetailMapper userPointDetailMapper;
    private final UserPointSettlementMapper userPointSettlementMapper;
    private final ReconcileService reconcileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean consumePoint(Long userId, String orderSn, Integer consumePoint, String source, String code) {
        log.info("[积分消耗] 开始消耗积分, userId={}, orderSn={}, consumePoint={}, source={}", 
                userId, orderSn, consumePoint, source);
        
        // 1. 校验用户积分是否充足
        UserPointDO userPoint = userPointMapper.selectByUserId(userId);
        if (userPoint == null || userPoint.getPoint() < consumePoint) {
            log.error("[积分消耗] 积分不足, userId={}, 需要={}, 可用={}", 
                    userId, consumePoint, userPoint == null ? 0 : userPoint.getPoint());
            throw new RuntimeException("积分不足");
        }
        
        // 2. 查询可用的积分明细（按过期时间排序）
        List<UserPointDetailDO> providerPointDetails = userPointDetailMapper.queryAvailableDetailsByUserId(userId);
        
        if (providerPointDetails == null || providerPointDetails.isEmpty()) {
            log.error("[积分消耗] 没有可用积分明细, userId={}", userId);
            throw new RuntimeException("没有可用积分明细");
        }
        
        // 3. 构建待消耗积分列表
        UserPointDetailDO consumeDetail = new UserPointDetailDO();
        consumeDetail.setUserId(userId);
        consumeDetail.setOrderSn(orderSn);
        consumeDetail.setTransactionId(UUID.randomUUID().toString());
        consumeDetail.setBusinessId(orderSn + "_consume");
        consumeDetail.setDelta(-consumePoint);
        consumeDetail.setRemainder(-consumePoint);
        consumeDetail.setState(PointTccStatusEnum.COMMITTED.name());
        consumeDetail.setType("积分抵扣");
        consumeDetail.setSource(source);
        consumeDetail.setCode(code);
        consumeDetail.setMessage("积分抵扣订单");
        consumeDetail.setCreateTime(LocalDateTime.now());
        
        List<UserPointDetailDO> consumeList = new ArrayList<>();
        consumeList.add(consumeDetail);
        
        // 4. 执行双向指针匹配
        consumePointDetails(userId, consumeList, providerPointDetails);
        
        // 5. 更新总积分
        userPointMapper.decreasePoint(userId, consumePoint);
        
        // 6. 保存消耗明细
        userPointDetailMapper.insert(consumeDetail);
        
        // 7. 触发平账（发放新积分时检查是否有未平账务）
        try {
            List<UserPointDetailDO> newPointDetails = new ArrayList<>();
            UserPointDetailDO newPoint = new UserPointDetailDO();
            newPoint.setUserId(userId);
            newPoint.setRemainder(consumePoint);
            newPoint.setSource(source);
            newPoint.setCode(code);
            newPointDetails.add(newPoint);
            reconcileService.reconcileSettlement(newPointDetails);
        } catch (Exception e) {
            log.error("[积分消耗] 平账失败，不影响主流程, userId={}", userId, e);
        }
        
        log.info("[积分消耗] 消耗积分成功, userId={}, orderSn={}, consumePoint={}", userId, orderSn, consumePoint);
        return true;
    }

    /**
     * 双向指针匹配算法
     * 
     * provider: 可用积分明细 (remainder > 0)
     * consumer: 待消耗积分 (delta < 0)
     */
    private void consumePointDetails(Long userId, List<UserPointDetailDO> consumerList, 
                                    List<UserPointDetailDO> providerList) {
        
        List<UserPointSettlementDO> settlements = new ArrayList<>();
        
        int i = 0, j = 0;
        
        while (i < providerList.size() && j < consumerList.size()) {
            UserPointDetailDO provider = providerList.get(i);
            UserPointDetailDO consumer = consumerList.get(j);
            
            Integer providerRemainder = provider.getRemainder() != null ? provider.getRemainder() : 0;
            Integer consumerRemainder = consumer.getRemainder() != null ? consumer.getRemainder() : 0;
            
            if (providerRemainder > 0 && consumerRemainder < 0) {
                // 计算可匹配的积分数量
                int allocated = Math.min(providerRemainder, Math.abs(consumerRemainder));
                
                // 更新双方remainder
                provider.setRemainder(providerRemainder - allocated);
                consumer.setRemainder(consumerRemainder + allocated);
                
                // 记录对账明细
                UserPointSettlementDO settlement = new UserPointSettlementDO();
                settlement.setUserId(userId);
                settlement.setConsumerSource(consumer.getSource());
                settlement.setConsumerCode(consumer.getCode());
                settlement.setProviderSource(provider.getSource());
                settlement.setProviderCode(provider.getCode());
                settlement.setConsumerTransactionId(consumer.getTransactionId());
                settlement.setConsumePoint(allocated);
                settlement.setProviderBusinessId(provider.getBusinessId());
                settlement.setConsumerBusinessId(consumer.getBusinessId());
                settlement.setCreateTime(LocalDateTime.now());
                settlement.setUpdateTime(LocalDateTime.now());
                settlements.add(settlement);
            }
            
            // 移动指针
            if (provider.getRemainder() != null && provider.getRemainder() == 0) {
                i++;
            }
            if (consumer.getRemainder() != null && consumer.getRemainder() == 0) {
                j++;
            }
        }
        
        // 批量更新发放方remainder
        if (i > 0) {
            List<UserPointDetailDO> updateList = providerList.subList(0, Math.min(i + 1, providerList.size()));
            for (UserPointDetailDO detail : updateList) {
                userPointDetailMapper.updateById(detail);
            }
        }
        
        // 批量保存对账记录
        if (!settlements.isEmpty()) {
            userPointSettlementMapper.batchInsert(settlements);
        }
    }

    @Override
    public Integer queryAvailablePoint(Long userId) {
        UserPointDO userPoint = userPointMapper.selectByUserId(userId);
        return userPoint != null ? userPoint.getPoint() : 0;
    }
}
