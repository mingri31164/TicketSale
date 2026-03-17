package com.mingri.train12306.biz.pointservice.service.impl;

import com.mingri.train12306.biz.pointservice.common.enums.PointTccStatusEnum;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointSettlementDO;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointDetailMapper;
import com.mingri.train12306.biz.pointservice.dao.mapper.UserPointSettlementMapper;
import com.mingri.train12306.biz.pointservice.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 平账服务实现
 * 实现文档中的ReconcileService核心逻辑
 * 
 * 平账触发场景：
 * 1. 积分发放时：当用户获得新积分时，检查是否存在未平账的消耗记录
 * 2. 积分回退时：当积分被回退时，触发平账修复remainder
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileServiceImpl implements ReconcileService {

    private final UserPointDetailMapper userPointDetailMapper;
    private final UserPointSettlementMapper userPointSettlementMapper;

    private static final int DEBT_QUERY_LIMIT = 1000;

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NESTED)
    public Integer reconcileSettlement(List<UserPointDetailDO> pointDetails) throws Exception {
        if (pointDetails == null || pointDetails.isEmpty()) {
            return 0;
        }

        // 1. 校验：必须是同一用户
        long distinctUserCount = pointDetails.stream()
                .map(UserPointDetailDO::getUserId)
                .distinct()
                .count();
        if (distinctUserCount != 1) {
            throw new IllegalArgumentException("平账明细必须是同一用户");
        }

        // 2. 校验：remainder必须大于0
        for (UserPointDetailDO detail : pointDetails) {
            if (detail.getRemainder() == null || detail.getRemainder() <= 0) {
                throw new IllegalArgumentException("平账明细remainder必须大于0");
            }
        }

        // 3. 执行平账
        return doReconcileSettlements(pointDetails);
    }

    private Integer doReconcileSettlements(List<UserPointDetailDO> pointDetails) {
        Long userId = pointDetails.get(0).getUserId();

        // 4. 查询所有未平的帐（remainder < 0）
        List<UserPointDetailDO> needToReconcileDetails = queryAllDebtDetails(userId);

        if (needToReconcileDetails == null || needToReconcileDetails.isEmpty()) {
            // 没有未平账务，直接返回剩余积分
            return pointDetails.stream()
                    .mapToInt(d -> d.getRemainder() != null ? d.getRemainder() : 0)
                    .sum();
        }

        // 5. 执行平账：双向指针匹配
        List<UserPointDetailDO> revertRemainderDetails = new ArrayList<>();
        List<UserPointSettlementDO> settlements = new ArrayList<>();
        int sumRevertPointCount = 0;

        for (UserPointDetailDO pointDetail : pointDetails) {
            Integer revertPointCount = pointDetail.getRemainder();
            Iterator<UserPointDetailDO> iterator = needToReconcileDetails.iterator();

            while (revertPointCount > 0 && iterator.hasNext()) {
                UserPointDetailDO needReconcile = iterator.next();
                int detailRemainder = Math.abs(needReconcile.getRemainder() != null ? needReconcile.getRemainder() : 0);

                if (detailRemainder == 0) {
                    iterator.remove();
                    continue;
                }

                // 计算本次平账消耗的积分
                int consumePoint;
                if (revertPointCount >= detailRemainder) {
                    // 新积分足以完全平掉这笔债务
                    needReconcile.setRemainder(0);
                    consumePoint = -detailRemainder;
                    iterator.remove(); // 债务已平，从列表移除
                    revertPointCount -= detailRemainder;
                } else {
                    // 部分平账
                    needReconcile.setRemainder(revertPointCount - detailRemainder);
                    consumePoint = -revertPointCount;
                    revertPointCount = 0;
                }

                revertRemainderDetails.add(needReconcile);

                // 6. 生成对账明细
                UserPointSettlementDO settlement = createSettlement(
                        userId,
                        needReconcile.getSource(),
                        needReconcile.getCode(),
                        pointDetail.getSource(),
                        pointDetail.getCode(),
                        needReconcile.getTransactionId(),
                        consumePoint,
                        pointDetail.getBusinessId(),
                        needReconcile.getBusinessId()
                );
                settlements.add(settlement);
            }

            // 更新剩余积分
            pointDetail.setRemainder(revertPointCount);
            revertRemainderDetails.add(pointDetail);
            sumRevertPointCount += revertPointCount;
        }

        // 7. 批量更新和保存
        if (!revertRemainderDetails.isEmpty()) {
            for (UserPointDetailDO detail : revertRemainderDetails) {
                userPointDetailMapper.updateById(detail);
            }
        }
        
        if (!settlements.isEmpty()) {
            userPointSettlementMapper.batchInsert(settlements);
        }

        log.info("[平账] 完成平账, userId={}, 剩余积分={}", userId, sumRevertPointCount);
        return sumRevertPointCount;
    }

    /**
     * 创建对账记录
     */
    private UserPointSettlementDO createSettlement(Long userId, String consumerSource, String consumerCode,
                                                   String providerSource, String providerCode,
                                                   String consumerTransactionId, int consumePoint,
                                                   String providerBusinessId, String consumerBusinessId) {
        UserPointSettlementDO settlement = new UserPointSettlementDO();
        settlement.setUserId(userId);
        settlement.setConsumerSource(consumerSource);
        settlement.setConsumerCode(consumerCode);
        settlement.setProviderSource(providerSource);
        settlement.setProviderCode(providerCode);
        settlement.setConsumerTransactionId(consumerTransactionId);
        settlement.setConsumePoint(consumePoint);
        settlement.setProviderBusinessId(providerBusinessId);
        settlement.setConsumerBusinessId(consumerBusinessId);
        settlement.setCreateTime(LocalDateTime.now());
        settlement.setUpdateTime(LocalDateTime.now());
        return settlement;
    }

    @Override
    public List<UserPointDetailDO> queryAllDebtDetails(Long userId) {
        List<UserPointDetailDO> needToReconcileDetails = new ArrayList<>();
        long startId = 0;

        while (true) {
            List<UserPointDetailDO> debtDetails = userPointDetailMapper.queryDebtListByUserId(
                    userId, startId, DEBT_QUERY_LIMIT);

            if (debtDetails == null || debtDetails.isEmpty()) {
                break;
            }

            needToReconcileDetails.addAll(debtDetails);
            startId = debtDetails.get(debtDetails.size() - 1).getId();

            if (debtDetails.size() < DEBT_QUERY_LIMIT) {
                break;
            }
        }

        return needToReconcileDetails;
    }
}
