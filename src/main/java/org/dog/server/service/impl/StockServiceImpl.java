package org.dog.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.dog.server.common.CacheKey;
import org.dog.server.entity.Stock;
import org.dog.server.entity.StockOrder;
import org.dog.server.mapper.StockMapper;
import org.dog.server.mapper.StockOrderMapper;
import org.dog.server.mapper.UserMapper;
import org.dog.server.service.StockOrderService;
import org.dog.server.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:57
 * @Description:
 */
@Service
public class StockServiceImpl extends ServiceImpl<StockMapper, Stock> implements StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockServiceImpl.class);

    @Resource
    private StockMapper stockMapper;

    @Resource
    private StockOrderMapper stockOrderMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public int createOrderV1(int sid) {
        // 校验库存
        Stock stock = checkStock(sid);
        // 更新库存
        saleStock(stock);
        // 创建订单
        return createOrder(stock);
    }

    @Override
    public int createOrderV2(int sid) {
        // 校验库存
        Stock stock = checkStock(sid);
        // 乐观锁更新库存
        boolean success = saleStockOptimistic(stock);
        if (!success) {
            throw new RuntimeException("过期库存version,更新失败");
        }
        return createOrder(stock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public int createOrderV3(int sid) {
        // 使用悲观锁校验库存
        Stock stock = stockMapper.selectByPrimaryKeyForUpdate(sid);
        // 更新库存
        saleStock(stock);
        // 创建订单
        return createOrder(stock);
    }

    @Override
    public int createVerifiedOrder(Integer sid, Integer userId, String verifyHash) throws Exception {
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + sid + "_" + userId;
        String verifyHashInRedis = (String) redisTemplate.opsForValue().get(hashKey);
        if (!verifyHash.equals(verifyHashInRedis)) {
            throw new Exception("hash值与Redis中不符合");
        }
        logger.info("验证hash值合法性成功");
        if (userMapper.selectById(userId) == null) {
            throw new Exception("用户不存在");
        }
        Stock stock = stockMapper.selectById(sid);
        if (stock == null) {
            throw new Exception("商品不存在");
        }
        boolean success = saleStockOptimistic(stockMapper.selectById(sid));
        if (!success){
            throw new RuntimeException("过期库存值，更新失败");
        }
        return createOrder(stock);
    }

    @Override
    public void setStockCountCache(int id) {
        Stock stock = stockMapper.selectById(id);
        int count = stock.getCount() - stock.getSale();
        String cacheKey = CacheKey.STOCK_COUNT.getKey() + "_" + id;
        String countStr = String.valueOf(count);
        logger.info("写入商品库存缓存 - [{}]:[{}]", cacheKey, countStr);
        redisTemplate.opsForValue().set(cacheKey,countStr,3600, TimeUnit.SECONDS);
    }

    @Override
    public void delStockCountCache(int sid) {
        String cacheKey = CacheKey.STOCK_COUNT.getKey() + "_" + sid;
        logger.info("删除key为[{}]的商品库存缓存", cacheKey);
        redisTemplate.delete(cacheKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class,propagation = Propagation.REQUIRED)
    public int createPessimisticOrder(int sid) {
        Stock stock = stockMapper.selectByPrimaryKeyForUpdate(sid);
        saleStock(stock);
        return createOrder(stock);
    }

    @Override
    public Stock checkStock(Integer sid) {
        Stock stock = stockMapper.selectById(sid);
        if (stock.getSale() >= stock.getCount()) throw new RuntimeException("库存不足");
        return stock;
    }

    @Override
    public Integer getStockCount(Integer sid) {
        Stock stock = checkStock(sid);
        return (stock.getCount() - stock.getSale());
    }

    private boolean saleStockOptimistic(Stock stock) {
        logger.info("查询数据库,尝试更新库存");
        int count = stockMapper.updateByOptimistic(stock);
        return count != 0;
    }

    private int createOrder(Stock stock) {
        StockOrder stockOrder = new StockOrder();
        stockOrder.setSid(stock.getId());
        stockOrder.setName(stock.getName());
        stockOrderMapper.insert(stockOrder);
        return stock.getCount() - (stock.getSale() + 1);
    }

    private void saleStock(Stock stock) {
        UpdateWrapper<Stock> uw = new UpdateWrapper<>();
        uw.lambda().set(Stock::getSale, stock.getSale() + 1).eq(Stock::getId, stock.getId());
        update(uw);
    }

}
