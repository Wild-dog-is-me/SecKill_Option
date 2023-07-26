package org.dog.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dog.server.entity.StockOrder;

/**
 * @Author: Odin
 * @Date: 2023/7/26 11:00
 * @Description:
 */

@Mapper
public interface StockOrderMapper extends BaseMapper<StockOrder> {
}
