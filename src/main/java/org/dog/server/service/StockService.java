package org.dog.server.service;

import org.dog.server.entity.Stock;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:58
 * @Description:
 */
public interface StockService {

    /**
     * 普通下单
     */
    int createOrderV1(int sid);

    /**
     * 乐观锁更新订单
     */
    int createOrderV2(int sid);

    /**
     * 悲观锁更新库存 事务for update更新库存
     */
    int createOrderV3(int sid);

    /**
     * 创建需要hash接口验证的订单
     */
    int createVerifiedOrder(Integer sid, Integer userId, String verifyHash) throws Exception;

    /**
     * 存放库存缓存
     */
    void setStockCountCache(int sid);

    /**
     * 删除库存缓存
     */
    void delStockCountCache(int sid);

    /**
     * 库存扣除下单事务
     */
    int createPessimisticOrder(int sid);

    /**
     * 检测库存是否还有
     */
    Stock checkStock(Integer sid);

    /**
     * 返回库存数
     */
    Integer getStockCount(Integer sid);
}
