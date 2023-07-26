package org.dog.server.common;

/**
 * @Author: Odin
 * @Date: 2023/7/26 12:03
 * @Description:
 */
public enum CacheKey {
    HASH_KEY("secKill_v1_user_hash"),
    LIMIT_KEY("secKill_v1_user_limit"),
    STOCK_COUNT("secKill_v1_stock_count"),
    USER_HAS_ORDER("secKill_v1_user_has_order");

    private String key;
    private CacheKey(String key) {
        this.key = key;
    }
    public String getKey() {
        return key;
    }
}
