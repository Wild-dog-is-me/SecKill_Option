package org.dog.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dog.server.entity.Stock;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:59
 * @Description:
 */

@Mapper
public interface StockMapper extends BaseMapper<Stock> {
    int updateByOptimistic(Stock stock);

    Stock selectByPrimaryKeyForUpdate(int sid);
}
