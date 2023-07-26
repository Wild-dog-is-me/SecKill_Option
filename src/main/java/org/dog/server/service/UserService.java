package org.dog.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dog.server.entity.User;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:58
 * @Description:
 */
public interface UserService extends IService<User> {
    String getVerifyHash(Integer sid, Integer userId) throws Exception;

    int addUserCount(Integer userId);

    boolean getUserIsBanned(Integer userId);
}
