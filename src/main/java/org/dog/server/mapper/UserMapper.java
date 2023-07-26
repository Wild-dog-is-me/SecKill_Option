package org.dog.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dog.server.entity.User;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:59
 * @Description:
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
