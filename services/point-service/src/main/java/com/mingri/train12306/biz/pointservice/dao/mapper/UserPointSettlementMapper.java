package com.mingri.train12306.biz.pointservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointSettlementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分对账/兑账表 Mapper
 */
@Mapper
public interface UserPointSettlementMapper extends BaseMapper<UserPointSettlementDO> {
    
    /**
     * 根据消费方事务ID查询对账记录
     */
    List<UserPointSettlementDO> selectByConsumerTransactionId(@Param("userId") Long userId, 
                                                              @Param("consumerTransactionId") String consumerTransactionId);
    
    /**
     * 根据提供方业务ID查询对账记录
     */
    List<UserPointSettlementDO> selectByProviderBusinessId(@Param("userId") Long userId,
                                                            @Param("providerBusinessId") String providerBusinessId);
    
    /**
     * 根据消费方业务ID查询对账记录
     */
    List<UserPointSettlementDO> selectByConsumerBusinessId(@Param("userId") Long userId,
                                                           @Param("consumerBusinessId") String consumerBusinessId);
    
    /**
     * 批量插入对账记录
     */
    int batchInsert(@Param("list") List<UserPointSettlementDO> settlements);
}
