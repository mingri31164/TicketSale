package com.mingri.train12306.biz.pointservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointTccDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 积分TCC事务记录Mapper
 */
@Mapper
public interface UserPointTccMapper extends BaseMapper<UserPointTccDO> {
    
    /**
     * 根据事务ID查询
     */
    UserPointTccDO selectByTransactionId(@Param("transactionId") String transactionId);
}

