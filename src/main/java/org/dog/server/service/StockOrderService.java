package org.dog.server.service;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:58
 * @Description:
 */
public interface StockOrderService {
    Boolean checkUserOrderInfoInCache(Integer sid, Integer userId);

    void createOrderByMQ(Integer sid, Integer userId) throws InterruptedException;

}
