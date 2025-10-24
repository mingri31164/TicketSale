package com.mingri.train12306.biz.pointservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDetailDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}

