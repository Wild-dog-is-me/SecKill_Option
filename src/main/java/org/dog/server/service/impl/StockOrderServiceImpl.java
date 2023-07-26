package org.dog.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dog.server.common.CacheKey;
import org.dog.server.entity.Stock;
import org.dog.server.entity.StockOrder;
import org.dog.server.mapper.StockMapper;
import org.dog.server.mapper.StockOrderMapper;
import org.dog.server.service.StockOrderService;
import org.dog.server.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:59
 * @Description:
 */
@Service
public class StockOrderServiceImpl extends ServiceImpl<StockOrderMapper, StockOrder> implements StockOrderService {

    private static final Logger logger = LoggerFactory.getLogger(StockOrderServiceImpl.class);

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private StockService stockService;

    @Resource
    private StockMapper stockMapper;

    @Resource
    private StockOrderMapper orderMapper;

    @Override
    public Boolean checkUserOrderInfoInCache(Integer sid, Integer userId) {
        String key = CacheKey.USER_HAS_ORDER.getKey() + "_" + sid;
        logger.info("检查用户ID: [{}] 是否抢购过商品ID: [{}] 检查key: [{}] ", userId, sid, key);
        return redisTemplate.opsForSet().isMember(key, userId.toString());
    }

    @Override
    public void createOrderByMQ(Integer sid, Integer userId) throws InterruptedException {

        // 模拟多个用户同时抢购，导致消息队列需要等待10秒
        TimeUnit.SECONDS.sleep(10);

        Stock stock;
        try {
            stock = stockService.checkStock(sid);
        } catch (Exception e) {
            logger.info("库存不足");
            return;
        }

        boolean updateStock = saleStockOptimistic(stock);
        if (!updateStock) {
            logger.warn("扣减库存失败，库存已经为0");
            return;
        }
        logger.info("扣减少库存成功,剩余库存:[{}]", stock.getCount() - stock.getSale() - 1);
        stockService.delStockCountCache(sid);
        logger.info("删除库存缓存");

        // 创建订单
        logger.info("写入订单至数据库");
        createOrderWithUserInfoInDB(stock, userId);
        logger.info("写入订单至缓存以供查询");
        createOrderWithUserInfoInCache(stock, userId);
        logger.info("下单完成");
    }

    private void createOrderWithUserInfoInCache(Stock stock, Integer userId) {
        String key = CacheKey.USER_HAS_ORDER.getKey() + "_" + userId;
        logger.info("写入用户订单数据set:[{}]:[{}]", key, userId.toString());
        redisTemplate.opsForSet().add(key, userId.toString());
    }

    private void createOrderWithUserInfoInDB(Stock stock, Integer userId) {
        StockOrder order = new StockOrder();
        order.setSid(stock.getId());
        order.setName(stock.getName());
        order.setUserId(userId);
        orderMapper.insert(order);
    }

    private boolean saleStockOptimistic(Stock stock) {
        logger.info("查询数据库，尝试更新缓存");
        int count = stockMapper.updateByOptimistic(stock);
        return count != 0;
    }
}
