package org.dog.server.mq;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: Odin
 * @Date: 2023/7/26 17:46
 * @Description:
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue delCacheQueue() {
        return new Queue("delCache");
    }

    @Bean
    public Queue orderQueue() {
        return new Queue("orderQueue");
    }
}
