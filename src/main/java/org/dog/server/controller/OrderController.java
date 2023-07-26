package org.dog.server.controller;

import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.RateLimiter;
import org.dog.server.common.AjaxResult;
import org.dog.server.service.StockOrderService;
import org.dog.server.service.StockService;
import org.dog.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:48
 * @Description:
 */

@RestController
@RequestMapping("/order")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    // 延时双删线程池
    private static ExecutorService cachedThreadPool =
            new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());


    // 延时时间：预估读数据库数据业务逻辑的耗时，用来做缓存再删除
    private static final int DELAY_MILLSECONDS = 1000;

    /**
     * 缓存再删除线程
     */
    public class delCacheByThread implements Runnable {

        private int sid;

        public delCacheByThread(int sid) {
            this.sid = sid;
        }

        @Override
        public void run() {
            try {
                logger.info("异步执行缓存双删，商品ID:[{}],首先休眠[{}]毫秒", sid, DELAY_MILLSECONDS);
                Thread.sleep(DELAY_MILLSECONDS);
                stockService.delStockCountCache(sid);
                logger.info("再次删除商品ID:[{}]缓存", sid);
            } catch (Exception e) {
                logger.error("删除失败:{}", e.getMessage());
            }
        }
    }

    //每秒放行10个请求
    RateLimiter rateLimiter = RateLimiter.create(10);

    @Resource
    private StockService stockService;

    @Resource
    private StockOrderService orderService;

    @Resource
    private UserService userService;

    @Resource
    private AmqpTemplate rabbitTemplate;


    /**
     * 普通下单
     */
    @GetMapping("/createOrderV1/{sid}")
    public AjaxResult createOrderV1(@PathVariable int sid) {
        logger.info("购买的商品ID:{}", sid);
        try {
            int count = stockService.createOrderV1(sid);
            logger.info("商品编号为【{}】的库存还剩【{}】", sid, count);
        } catch (Exception e) {
            return AjaxResult.error();
        }
        return AjaxResult.success();
    }

    /**
     * 下单 - 使用乐观锁防止出现超卖
     */
    @GetMapping("/createOrderV2/{sid}")
    public AjaxResult createOrderV2(@PathVariable int sid) {
        logger.info("购买商品的ID为:{}", sid);
        try {
            int count = stockService.createOrderV2(sid);
            logger.info("商品编号为【{}】的库存还剩【{}】", sid, count);
        } catch (Exception e) {
            return AjaxResult.error();
        }
        return AjaxResult.success();
    }

    /**
     * 下单 - 使用限流+乐观锁解决超卖 - 非阻塞式获取令牌
     */
    @GetMapping("/createOrderV3/{sid}")
    public AjaxResult createOrderV3(@PathVariable int sid) {
        logger.info("购买商品的ID为:{}", sid);
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)) {
            logger.warn("限流了");
            return AjaxResult.success("购买失败,被限流了");
        }
        try {
            int count = stockService.createOrderV2(sid);
            logger.info("商品编号为【{}】的库存还剩【{}】", sid, count);
        } catch (Exception e) {
            return AjaxResult.error();
        }
        return AjaxResult.success();
    }

    /**
     * 下单 - 使用限流+乐观锁解决超卖 - 阻塞式获取令牌
     */
    @GetMapping("/createOrderV4/{sid}")
    public AjaxResult createOrderV4(@PathVariable int sid) {
        logger.info("购买商品的ID为:{}", sid);
        logger.info("等待时间" + rateLimiter.acquire());
        try {
            int count = stockService.createOrderV2(sid);
            logger.info("商品编号为【{}】的库存还剩【{}】", sid, count);
        } catch (Exception e) {
            return AjaxResult.error();
        }
        return AjaxResult.success();
    }

    /**
     * 下单 - 悲观锁更新库存 事务for update更新库存
     */
    @GetMapping("/createOrderV5/{sid}")
    public AjaxResult createOrderV5(@PathVariable int sid) {
        logger.info("购买商品的ID为:{}", sid);
        try {
            int count = stockService.createOrderV3(sid);
            logger.info("商品编号为【{}】的库存还剩【{}】", sid, count);
        } catch (Exception e) {
            return AjaxResult.error();
        }
        return AjaxResult.success();
    }

    /**
     * 根据用户ID 商品ID 生成hash验证值
     */
    @GetMapping("/getVerifyHash")
    public AjaxResult getVerifyHash(@RequestParam(value = "sid") Integer sid, @RequestParam(value = "userId") Integer userId) {
        String hash;
        try {
            hash = userService.getVerifyHash(sid, userId);
        } catch (Exception e) {
            return AjaxResult.error("获取hash失败");
        }
        return AjaxResult.success(String.format("请求抢购验证的hash值为:%s", hash));
    }

    /**
     * 需要验证的下单接口
     */
    @GetMapping("/createOrderWithVerifiedUrl")
    public AjaxResult createOrderWithVerifiedUrl(@RequestParam(value = "sid") Integer sid,
                                                 @RequestParam(value = "userId") Integer userId,
                                                 @RequestParam(value = "verifyHash") String verifyHash) {
        int count;
        try {
            count = stockService.createVerifiedOrder(sid, userId, verifyHash);
            logger.info("购买成功，剩余库存为: [{}]", count);
        } catch (Exception e) {
            return AjaxResult.error("下单失败");
        }
        return AjaxResult.success(String.format("购买成功，剩余库存为：%d", count));
    }

    /**
     * 单用户限流 + 接口验证下单
     */
    @GetMapping("/createOrderWithVerifiedUrlAndLimit")
    public AjaxResult createOrderWithVerifiedUrlAndLimit(@RequestParam(value = "sid") Integer sid,
                                                         @RequestParam(value = "userId") Integer userId,
                                                         @RequestParam(value = "verifyHash") String verifyHash) {
        int inventory;
        int count = userService.addUserCount(userId);
        logger.info("用户截至该次的访问次数为: [{}]", count);
        boolean isBanned = userService.getUserIsBanned(userId);
        logger.error("isBanned:{}", isBanned);
        if (isBanned) {
            return AjaxResult.error("下单失败");
        }
        try {
            inventory = stockService.createVerifiedOrder(sid, userId, verifyHash);
            logger.info("购买成功，剩余库存为: [{}]", count);
        } catch (Exception e) {
            return AjaxResult.error("下单失败");
        }
        return AjaxResult.success(String.format("购买成功，剩余库存为：%d", inventory));
    }

    /**
     * 缓存库存接口
     */
    @GetMapping("/cacheStock/{sid}")
    public AjaxResult cacheStock(@PathVariable int sid) {
        stockService.setStockCountCache(sid);
        return AjaxResult.success();
    }

    /**
     * 先删除缓存，再更新数据库
     */
    @GetMapping("/createOrderWithCacheV1/{sid}")
    public AjaxResult createOrderWithCacheV1(@PathVariable int sid) {
        int count;
        try {
            // 删除库存缓存
            stockService.delStockCountCache(sid);
            // 完成库存扣除下单事务
            count = stockService.createPessimisticOrder(sid);
            stockService.setStockCountCache(sid);
        } catch (Exception e) {
            logger.error("购买失败：[{}]", e.getMessage());
            return AjaxResult.error("下单失败");
        }
        logger.info("购买成功，剩余库存为: [{}]", count);
        return AjaxResult.success(String.format("购买成功，剩余库存为：%d", count));
    }

    /**
     * 先更新数据库，再删除缓存
     */
    @GetMapping("/createOrderWithCacheV2/{sid}")
    public AjaxResult createOrderWithCacheV2(@PathVariable int sid) {
        int count;
        try {
            // 完成库存扣除下单事务
            count = stockService.createPessimisticOrder(sid);
            // 删除库存缓存
            stockService.delStockCountCache(sid);
            stockService.setStockCountCache(sid);
        } catch (Exception e) {
            logger.error("购买失败：[{}]", e.getMessage());
            return AjaxResult.error("下单失败");
        }
        logger.info("购买成功，剩余库存为: [{}]", count);
        return AjaxResult.success(String.format("购买成功，剩余库存为：%d", count));
    }

    /**
     * 延时双删
     */
    @GetMapping("/createOrderWithCacheV3/{sid}")
    public AjaxResult createOrderWithCacheV3(@PathVariable int sid) {
        int count;
        try {
            // 删除库存缓存
            stockService.delStockCountCache(sid);
            // 完成扣库存下单事务
            count = stockService.createPessimisticOrder(sid);
            logger.info("完成下单事务");
            // 延时指定时间后再次删除
            cachedThreadPool.execute(new delCacheByThread(sid));
        } catch (Exception e) {
            logger.error("购买失败[{}]", e.getMessage());
            return AjaxResult.error();
        }
        logger.info("购买成功，剩余库存为:{}", count);
        return AjaxResult.success();
    }

    /**
     * 下单接口：先更新数据库，再删缓存，删除缓存重试机制
     */
    @RequestMapping("/createOrderWithCacheV4/{sid}")
    public AjaxResult createOrderWithCacheV4(@PathVariable int sid) {
        int count;
        try {
            // 完成扣库存下单事务
            count = stockService.createPessimisticOrder(sid);
            logger.info("下单完成，商品id{}", sid);
            // 假设延迟删除失败
            // cachedThreadPool.execute(new delCacheByThread(sid));
            // 通知消息队列删除缓存
            sendToDelCache(String.valueOf(sid));
        } catch (Exception e) {
            return AjaxResult.error();
        }
        return AjaxResult.success(String.format("购买成功，剩余库存为：%d", count));
    }

    private void sendToDelCache(String message) {
        logger.info("通知消息队列开始重试删除缓存：[{}]", message);
        this.rabbitTemplate.convertAndSend("delCache", message);
    }

    /**
     * 下单接口：异步处理订单
     */
    @GetMapping("/createUserOrderWithMQ")
    public AjaxResult createUserOrderWithMQ(@RequestParam("sid") Integer sid, @RequestParam("userId") Integer userId) {
        try {
            Boolean hasOrder = orderService.checkUserOrderInfoInCache(sid, userId);
            if (hasOrder != null && hasOrder) {
                logger.info("该用户已经抢购过");
                return AjaxResult.success("你已经抢购过了，不要太贪心了");
            }
            logger.info("当前用户没有抢购过，检查缓存中商品是否还有库存");
            Integer count = stockService.getStockCount(sid);
            if (count == 0) {
                return AjaxResult.error("抢购失败，库存不足");
            }
            // 有库存，则将用户id和商品id封装为消息体传给消息队列处理
            // 注意这里的有库存和已经下单都是缓存中的结论，存在不可靠性，在消息队列中会查表再次验证
            logger.info("有库存，库存数量为:{}", count);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("sid", sid);
            jsonObject.put("userId", userId);
            sendToOrderQueue(jsonObject.toJSONString());
            return AjaxResult.success("提交秒杀请求成功");
        } catch (Exception e) {
            logger.error("下单接口异步处理异常{}", e.getMessage());
            return AjaxResult.error("秒杀请求失败，服务器正忙...");
        }
    }

    private void sendToOrderQueue(String message) {
        logger.info("通知消息队列开始下单:[{}]", message);
        this.rabbitTemplate.convertAndSend("orderQueue", message);
    }

    /**
     * 检查缓存中用户是否已经生成订单
     */
    @GetMapping("/checkOrderByUserIdInCache")
    public AjaxResult checkOrderByUserIdInCache(@RequestParam(value = "sid") Integer sid,
                                                @RequestParam(value = "userId") Integer userId) {
        try {
            Boolean hasOrder = orderService.checkUserOrderInfoInCache(sid, userId);
            if (hasOrder != null && hasOrder) {
                return AjaxResult.success("恭喜你，抢购成功");
            }
        } catch (Exception e) {
            logger.error("检查订单异常:{}", e.getMessage());
        }
        return AjaxResult.success("很抱歉，你的订单尚未生成，请继续排队");
    }
}
