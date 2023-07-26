package org.dog.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dog.server.common.CacheKey;
import org.dog.server.entity.Stock;
import org.dog.server.entity.User;
import org.dog.server.mapper.StockMapper;
import org.dog.server.mapper.UserMapper;
import org.dog.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:59
 * @Description:
 */

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final String SALT = "randomString";

    private static final int ALLOW_COUNT = 10;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StockMapper stockMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public String getVerifyHash(Integer sid, Integer userId) throws Exception {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new Exception("用户不存在");
        }
        logger.info("用户信息为:{}", user.toString());
        Stock stock = stockMapper.selectById(sid);
        if (stock == null) {
            throw new Exception("商品不存在");
        }
        logger.info("商品信息为:{}", stock.toString());
        String verify = SALT + sid + userId;
        String verifyHash = DigestUtils.md5DigestAsHex(verify.getBytes());
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + sid + "_" + userId;
        redisTemplate.opsForValue().set(hashKey, verifyHash, 3600, TimeUnit.SECONDS);
        logger.info("Redis写入 -【{}】:【{}】", hashKey, verifyHash);
        return verifyHash;
    }

    @Override
    public int addUserCount(Integer userId) {
        String limitKey = CacheKey.LIMIT_KEY.getKey() + "_" + userId;
        redisTemplate.opsForValue().setIfAbsent(limitKey,0, 3600, TimeUnit.SECONDS);
        Long limit = redisTemplate.opsForValue().increment(limitKey);
        return Integer.parseInt(String.valueOf(limit));
    }

    @Override
    public boolean getUserIsBanned(Integer userId) {
        String limitKey = CacheKey.LIMIT_KEY.getKey() + "_" + userId;
        Integer limitNum = (Integer) redisTemplate.opsForValue().get(limitKey);
        if (limitNum == null) {
            logger.error("该用户没有访问申请验证值记录，疑似异常");
            return true;
        }
        return limitNum > ALLOW_COUNT;
    }
}
