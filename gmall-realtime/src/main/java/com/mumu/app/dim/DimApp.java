package com.mumu.app.dim;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mumu.app.func.DimSinkFunction;
import com.mumu.app.func.TableProcessFunction;
import com.mumu.bean.TableProcess;
import com.mumu.utils.MyKafkaUtil;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;

import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.streaming.api.datastream.BroadcastConnectedStream;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

//数据流：web/app->nginx->业务服务器-》Mysql(binlog)->Maxwell->kafka(ODS)->FlinkApp->Phoenix
//程序：Mock->Mysql(binlog)->Maxwell->kafka(zk)->DimApp(FlinkCDC/Mysql)->Phoenix(HBase/ZK/HDFS)

public class DimApp {
    public static void main(String[] args) throws Exception {
        //TODO 1.获取执行环境
        StreamExecutionEnvironment env=StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);//生产环境中设置为kafka主题分区数
//        //1.1 开启checkPoint 生产环境开启
//        env.enableCheckpointing(5*60000L, CheckpointingMode.EXACTLY_ONCE);
//        env.getCheckpointConfig().setCheckpointTimeout(10*60000L);
//        env.getCheckpointConfig().setMaxConcurrentCheckpoints(2);
//        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3,5000L));
//        //1.2 设置状态后端
//        env.setStateBackend(new HashMapStateBackend());
//        env.getCheckpointConfig().setCheckpointStorage("hdfs://hadoop102:8082/211126/ck");
//        System.setProperty("HADOOP_USER_NAME","mumu");


        //TODO 2.读取kafka topic_db主题数据创建主流
        String topic ="topic_db";
        String groupId = "dim_app_211126";
        DataStreamSource<String> kafkaDS = env.addSource(MyKafkaUtil.getFlinkKafkaConsumer(topic,groupId));

        //TODO 3.过滤掉非json数据&保留新增、变化以及初始化数据并将数据转化为json格式
        SingleOutputStreamOperator<JSONObject> filterJsonObjDS = kafkaDS.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String s, Collector<JSONObject> collector) throws Exception {
                try {
                    JSONObject jsonObject = JSON.parseObject(s);
                    //获取数据中的操作类型字段
                    String type = jsonObject.getString("type");
                    //保留新增、变化、及初始化数据
                    if("insert".equals(type)||"update".equals(type)||"bootstrap-insert".equals(type)){
                        collector.collect(jsonObject);
                    }
                } catch (Exception e) {
                    System.out.println("发现脏数据："+s);
                }
            }
        });

        //TODO 4.使用FlinkCDC读取Mysql配置信息表创建配置流
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
        DataStreamSource<String> mysqlSourceDS = env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(),"MysqlSource");
        //TODO 5.将配置流处理为广播流
        MapStateDescriptor<String, TableProcess> mapStateDescriptor=new MapStateDescriptor<>("map-state",String.class,TableProcess.class );
        BroadcastStream<String> broadcastStream = mysqlSourceDS.broadcast(mapStateDescriptor);
        //TODO 6.链接主流与广播流
        BroadcastConnectedStream<JSONObject, String> connectedStream = filterJsonObjDS.connect(broadcastStream);

        //TODO 7.处理链接流，根据配置信息处理主流数据
        SingleOutputStreamOperator<JSONObject> dimDS = connectedStream.process(new TableProcessFunction(mapStateDescriptor));

        //TODO 8.将数据写出到phoenix
        dimDS.print(">>>>>>>>");
        dimDS.addSink(new DimSinkFunction());
        //TODO 9.启动任务
        env.execute("DimApp");
    }
}
