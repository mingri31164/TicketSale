package com.mingri.train12306.biz.pointservice.service;

import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;

import java.util.List;

/**
 * 平账服务接口
 * 实现实时平账机制：当用户获得新积分时，检查是否存在未平账的消耗记录
 */
public interface ReconcileService {
    
    /**
     * 根据平账详情进行平账
     * @param pointDetails 待平账的积分明细列表
     * @return 平账完成后剩余积分数
     */
    Integer reconcileSettlement(List<UserPointDetailDO> pointDetails) throws Exception;
    
    /**
     * 查询用户所有未平的帐（remainder < 0）
     */
    List<UserPointDetailDO> queryAllDebtDetails(Long userId);
}
