package com.mingri.train12306.biz.pointservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户积分变动明细Mapper
 */
@Mapper
public interface UserPointDetailMapper extends BaseMapper<UserPointDetailDO> {
    
    /**
     * 根据业务ID查询
     */
    UserPointDetailDO selectByBusinessId(@Param("businessId") String businessId);
    
    /**
     * 根据事务ID查询
     */
    UserPointDetailDO selectByTransactionId(@Param("transactionId") String transactionId);
    
    /**
     * 查询用户所有未平的帐（remainder < 0）
     * 用于平账计算
     */
    List<UserPointDetailDO> queryDebtListByUserId(@Param("userId") Long userId, 
                                                  @Param("startId") Long startId, 
                                                  @Param("limitSize") Integer limitSize);
    
    /**
     * 查询用户可用积分明细（remainder > 0，按过期时间排序）
     * 用于积分消耗时的最优匹配
     */
    List<UserPointDetailDO> queryAvailableDetailsByUserId(@Param("userId") Long userId);
    
    /**
     * 批量更新remainder
     */
    int batchUpdateRemainder(@Param("list") List<UserPointDetailDO> details);
    /**
     * 查询用户可用积分总额
     */
    Integer queryTotalCanConsume(@Param("userId") Long userId);
}

