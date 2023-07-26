package org.dog.server.mq.receiver;

import com.alibaba.fastjson.JSONObject;
import org.dog.server.service.StockOrderService;
import org.dog.server.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: Odin
 * @Date: 2023/7/26 17:46
 * @Description:
 */
@Component
@RabbitListener(queues = "orderQueue")
public class OrderMQReceiver {

    private static final Logger logger = LoggerFactory.getLogger(OrderMQReceiver.class);

    @Resource
    private StockOrderService orderService;

    @RabbitHandler
    public void process(String message) {
        logger.info("OrderMQReceiver收到消息开始用户下单流程:{}", message);
        JSONObject jsonObject = JSONObject.parseObject(message);
        try {
            orderService.createOrderByMQ(jsonObject.getInteger("sid"), jsonObject.getInteger("userId"));
        } catch (Exception e) {
            logger.error("消息处理异常:{}", e.getMessage());
        }
    }
}
