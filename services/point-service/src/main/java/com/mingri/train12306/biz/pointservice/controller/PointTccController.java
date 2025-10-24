package com.mingri.train12306.biz.pointservice.controller;

import com.mingri.train12306.biz.pointservice.dto.req.PointTccReqDTO;
import com.mingri.train12306.biz.pointservice.dto.resp.PointTccRespDTO;
import com.mingri.train12306.biz.pointservice.service.PointTccService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 积分TCC控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/point/tcc")
@RequiredArgsConstructor
public class PointTccController {

    private final PointTccService pointTccService;

    /**
     * Try：冻结积分（购票获得积分场景）
     */
    @PostMapping("/freeze/try")
    public PointTccRespDTO tryFreezePoint(@RequestBody PointTccReqDTO reqDTO) {
        try {
            boolean success = pointTccService.tryFreezePoint(
                    reqDTO.getTransactionId(),
                    reqDTO.getUserId(),
                    reqDTO.getOrderSn(),
                    reqDTO.getPoint()
            );
            return new PointTccRespDTO(success, success ? "冻结积分成功" : "冻结积分失败");
        } catch (Exception e) {
            log.error("Try冻结积分异常", e);
            return new PointTccRespDTO(false, e.getMessage());
        }
    }

    /**
     * Confirm：确认发放积分
     */
    @PostMapping("/freeze/confirm")
    public PointTccRespDTO confirmGrantPoint(@RequestParam String transactionId) {
        try {
            boolean success = pointTccService.confirmGrantPoint(transactionId);
            return new PointTccRespDTO(success, success ? "发放积分成功" : "发放积分失败");
        } catch (Exception e) {
            log.error("Confirm发放积分异常", e);
            return new PointTccRespDTO(false, e.getMessage());
        }
    }

    /**
     * Cancel：取消冻结积分
     */
    @PostMapping("/freeze/cancel")
    public PointTccRespDTO cancelFreezePoint(@RequestParam String transactionId) {
        try {
            boolean success = pointTccService.cancelFreezePoint(transactionId);
            return new PointTccRespDTO(success, success ? "取消冻结成功" : "取消冻结失败");
        } catch (Exception e) {
            log.error("Cancel取消冻结异常", e);
            return new PointTccRespDTO(false, e.getMessage());
        }
    }

    /**
     * Try：冻结并扣减积分（积分抵扣场景）
     */
    @PostMapping("/consume/try")
    public PointTccRespDTO tryConsumePoint(@RequestBody PointTccReqDTO reqDTO) {
        try {
            boolean success = pointTccService.tryConsumePoint(
                    reqDTO.getTransactionId(),
                    reqDTO.getUserId(),
                    reqDTO.getOrderSn(),
                    reqDTO.getPoint()
            );
            return new PointTccRespDTO(success, success ? "冻结扣减积分成功" : "冻结扣减积分失败");
        } catch (Exception e) {
            log.error("Try冻结扣减积分异常", e);
            return new PointTccRespDTO(false, e.getMessage());
        }
    }

    /**
     * Confirm：确认扣减积分
     */
    @PostMapping("/consume/confirm")
    public PointTccRespDTO confirmConsumePoint(@RequestParam String transactionId) {
        try {
            boolean success = pointTccService.confirmConsumePoint(transactionId);
            return new PointTccRespDTO(success, success ? "扣减积分成功" : "扣减积分失败");
        } catch (Exception e) {
            log.error("Confirm扣减积分异常", e);
            return new PointTccRespDTO(false, e.getMessage());
        }
    }

    /**
     * Cancel：归还扣减的积分
     */
    @PostMapping("/consume/cancel")
    public PointTccRespDTO cancelConsumePoint(@RequestParam String transactionId) {
        try {
            boolean success = pointTccService.cancelConsumePoint(transactionId);
            return new PointTccRespDTO(success, success ? "归还积分成功" : "归还积分失败");
        } catch (Exception e) {
            log.error("Cancel归还积分异常", e);
            return new PointTccRespDTO(false, e.getMessage());
        }
    }
}

