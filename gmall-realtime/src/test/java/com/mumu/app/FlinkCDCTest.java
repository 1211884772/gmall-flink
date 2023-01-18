package com.mumu.app;

import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class FlinkCDCTest {
    public static void main(String[] args) throws Exception {
        //1.创建执行环境

        StreamExecutionEnvironment env =StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        //2.创建Flink-MySQL-CDC的Source
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname("hadoop102")
                .port(3306)
                .username("root")
                .password("123456")
                .databaseList("gmall_config")
                .tableList("gmall_config.table_process")
                .deserializer(new JsonDebeziumDeserializationSchema())
                .startupOptions(StartupOptions.initial())
                .build();


        //3.使用CDC Source从MySQL读取数据
        DataStreamSource<String> dataStreamSource = env.fromSource(
                mySqlSource,
                WatermarkStrategy.noWatermarks(),
                "MysqlSource");


        //4.打印数据
        dataStreamSource.print();
        //5.执行任务
        env.execute();
    }
}
