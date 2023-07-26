package org.dog.server.mq.receiver;

import org.dog.server.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author: Odin
 * @Date: 2023/7/26 17:46
 * @Description:
 */
@Component
@RabbitListener(queues = "delCache")
public class DelCacheReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelCacheReceiver.class);

    @Autowired
    private StockService stockService;

    @RabbitHandler
    public void process(String message) {
        LOGGER.info("DelCacheReceiver收到消息: " + message);
        LOGGER.info("DelCacheReceiver开始删除缓存: " + message);
        stockService.delStockCountCache(Integer.parseInt(message));
    }
}
