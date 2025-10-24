package com.mingri.train12306.biz.pointservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mingri.train12306.biz.pointservice.dao.entity.UserPointDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户积分账户Mapper
 */
@Mapper
public interface UserPointMapper extends BaseMapper<UserPointDO> {
    
    /**
     * 根据用户ID查询
     */
    UserPointDO selectByUserId(@Param("userId") Long userId);
    
    /**
     * 增加可用积分
     */
    int increasePoint(@Param("userId") Long userId, @Param("point") Integer point);
    
    /**
     * 扣减可用积分
     */
    int decreasePoint(@Param("userId") Long userId, @Param("point") Integer point);
    
    /**
     * 增加冻结积分
     */
    int increaseFrozenPoint(@Param("userId") Long userId, @Param("point") Integer point);
    
    /**
     * 扣减冻结积分
     */
    int decreaseFrozenPoint(@Param("userId") Long userId, @Param("point") Integer point);
    
    /**
     * 增加累计积分
     */
    int increaseTotalPoint(@Param("userId") Long userId, @Param("point") Integer point);
}

