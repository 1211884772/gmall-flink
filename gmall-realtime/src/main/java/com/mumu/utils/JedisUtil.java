package com.mumu.utils;

import com.mumu.common.GmallConfig;
import redis.clients.jedis.*;

import java.util.LinkedList;
import java.util.List;

public class JedisUtil {

    private static ShardedJedisPool  jedisPool;

    private static void initJedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(5);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWaitMillis(2000);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        JedisShardInfo jedisShardInfo1 = new JedisShardInfo(GmallConfig.REDIS_URL);
        jedisShardInfo1.setConnectionTimeout(10000);
        jedisShardInfo1.setUser(GmallConfig.REDIS_USER);
        jedisShardInfo1.setPassword(GmallConfig.REDIS_PASSWORD);

        List<JedisShardInfo> list = new LinkedList<JedisShardInfo>();
        list.add(jedisShardInfo1);
        jedisPool = new ShardedJedisPool(poolConfig, list);
    }

    public static ShardedJedis  getJedis() {
        if (jedisPool == null) {
            initJedisPool();
        }
        // 获取Jedis客户端
        return jedisPool.getResource();
    }

    public static void main(String[] args) {
        ShardedJedis jedis = getJedis();
        String pong = jedis.set("myname","lxr");
        jedis.expire("myname", 60);
        System.out.println(pong);
        jedis.close();
    }

}
