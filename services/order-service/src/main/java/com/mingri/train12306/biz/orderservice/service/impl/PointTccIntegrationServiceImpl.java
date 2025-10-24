package com.mingri.train12306.biz.orderservice.service.impl;

import com.mingri.train12306.biz.orderservice.common.util.PointCalculateUtil;
import com.mingri.train12306.biz.orderservice.remote.PointRemoteService;
import com.mingri.train12306.biz.orderservice.remote.dto.PointTccReqDTO;
import com.mingri.train12306.biz.orderservice.remote.dto.PointTccRespDTO;
import com.mingri.train12306.biz.orderservice.service.PointTccIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 积分TCC集成服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointTccIntegrationServiceImpl implements PointTccIntegrationService {

    private final PointRemoteService pointRemoteService;

    @Override
    public String freezePointOnCreateOrder(String orderSn, Long userId, Integer orderAmount) {
        try {
            // 1. 计算应得积分
            Integer earnPoint = PointCalculateUtil.calculateEarnPointByCent(orderAmount);
            if (earnPoint <= 0) {
                log.info("订单金额太低，无需冻结积分, orderSn={}, orderAmount={}", orderSn, orderAmount);
                return null;
            }

            // 2. 生成TCC事务ID
            String tccTransactionId = "TCC_" + UUID.randomUUID().toString().replace("-", "");

            // 3. 调用积分服务Try接口
            PointTccReqDTO reqDTO = new PointTccReqDTO(tccTransactionId, userId, orderSn, earnPoint);
            PointTccRespDTO respDTO = pointRemoteService.tryFreezePoint(reqDTO);

            if (respDTO != null && Boolean.TRUE.equals(respDTO.getSuccess())) {
                log.info("冻结积分成功, orderSn={}, userId={}, earnPoint={}, tccTransactionId={}", 
                        orderSn, userId, earnPoint, tccTransactionId);
                return tccTransactionId;
            } else {
                log.error("冻结积分失败, orderSn={}, userId={}, earnPoint={}, message={}", 
                        orderSn, userId, earnPoint, respDTO != null ? respDTO.getMessage() : "null");
                return null;
            }
        } catch (Exception e) {
            log.error("冻结积分异常, orderSn={}, userId={}", orderSn, userId, e);
            return null;
        }
    }

    @Override
    public void grantPointOnPaySuccess(String tccTransactionId) {
        if (tccTransactionId == null || tccTransactionId.isEmpty()) {
            log.info("无需发放积分, tccTransactionId为空");
            return;
        }

        try {
            PointTccRespDTO respDTO = pointRemoteService.confirmGrantPoint(tccTransactionId);
            if (respDTO != null && Boolean.TRUE.equals(respDTO.getSuccess())) {
                log.info("发放积分成功, tccTransactionId={}", tccTransactionId);
            } else {
                log.error("发放积分失败, tccTransactionId={}, message={}", 
                        tccTransactionId, respDTO != null ? respDTO.getMessage() : "null");
            }
        } catch (Exception e) {
            log.error("发放积分异常, tccTransactionId={}", tccTransactionId, e);
        }
    }

    @Override
    public void cancelPointOnPayFail(String tccTransactionId) {
        if (tccTransactionId == null || tccTransactionId.isEmpty()) {
            log.info("无需取消积分, tccTransactionId为空");
            return;
        }

        try {
            PointTccRespDTO respDTO = pointRemoteService.cancelFreezePoint(tccTransactionId);
            if (respDTO != null && Boolean.TRUE.equals(respDTO.getSuccess())) {
                log.info("取消积分成功, tccTransactionId={}", tccTransactionId);
            } else {
                log.error("取消积分失败, tccTransactionId={}, message={}", 
                        tccTransactionId, respDTO != null ? respDTO.getMessage() : "null");
            }
        } catch (Exception e) {
            log.error("取消积分异常, tccTransactionId={}", tccTransactionId, e);
        }
    }
}

