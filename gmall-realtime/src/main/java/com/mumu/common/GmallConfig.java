package com.mumu.common;

import redis.clients.jedis.JedisShardInfo;

public class GmallConfig {
    // Phoenix库名
    public static final String HBASE_SCHEMA = "GMALL211126_REALTIME";

    // Phoenix驱动
    public static final String PHOENIX_DRIVER = "org.apache.phoenix.jdbc.PhoenixDriver";

    // Phoenix连接参数
    public static final String PHOENIX_SERVER = "jdbc:phoenix:hadoop102,hadoop103,hadoop104:2181";

    // ClickHouse 驱动
    public static final String CLICKHOUSE_DRIVER = "ru.yandex.clickhouse.ClickHouseDriver";

    // ClickHouse 连接 URL
    public static final String CLICKHOUSE_URL = "jdbc:clickhouse://hadoop102:8123/gmall_211126";


    //Redis主机，端口，用户，密码
    public static final String REDIS_URL= "redis://hadoop102:6379/0";
    public static final String REDIS_USER = "mumu";
    public static final String REDIS_PASSWORD = "123456";

    //


}
