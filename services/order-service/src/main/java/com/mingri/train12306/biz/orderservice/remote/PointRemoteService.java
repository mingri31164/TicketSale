package com.mingri.train12306.biz.orderservice.remote;

import com.mingri.train12306.biz.orderservice.remote.dto.PointTccReqDTO;
import com.mingri.train12306.biz.orderservice.remote.dto.PointTccRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 积分服务远程调用接口
 */
@FeignClient(value = "train12306-point-service", url = "${point.service.url:}")
public interface PointRemoteService {
    
    /**
     * Try：冻结积分（购票获得积分场景）
     */
    @PostMapping("/api/point/tcc/freeze/try")
    PointTccRespDTO tryFreezePoint(@RequestBody PointTccReqDTO reqDTO);
    
    /**
     * Confirm：确认发放积分
     */
    @PostMapping("/api/point/tcc/freeze/confirm")
    PointTccRespDTO confirmGrantPoint(@RequestParam("transactionId") String transactionId);
    
    /**
     * Cancel：取消冻结积分
     */
    @PostMapping("/api/point/tcc/freeze/cancel")
    PointTccRespDTO cancelFreezePoint(@RequestParam("transactionId") String transactionId);
}

