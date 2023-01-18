package com.mumu.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mumu.app.func.DimAsyncFunction;
import com.mumu.bean.TradeProvinceOrderWindow;
import com.mumu.utils.DateFormatUtil;
import com.mumu.utils.MyClickHouseUtil;
import com.mumu.utils.MyKafkaUtil;
import com.mumu.utils.TimestampLtz3CompareUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

//数据流：Web/app -> nginx -> 业务服务器(Mysql) -> Maxwell -> Kafka(ODS) -> FlinkApp -> Kafka(DWD) -> FlinkApp -> Kafka(DWD) -> FlinkApp -> ClickHouse(DWS)
//程  序：Mock  ->  Mysql  ->  Maxwell -> Kafka(ZK)  ->  DwdTradeOrderPreProcess -> Kafka(ZK) -> DwdTradeOrderDetail -> Kafka(ZK) -> DwsTradeProvinceOrderWindow(Phoenix-(HBase-HDFS、ZK)、Redis) -> ClickHouse(ZK)
public class DwsTradeProvinceOrderWindow {
    public static void main(String[] args) throws Exception {
        //TODO 1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); //生产环境中设置为Kafka主题的分区数

        //1.1 开启CheckPoint
        //env.enableCheckpointing(5 * 60000L, CheckpointingMode.EXACTLY_ONCE);
        //env.getCheckpointConfig().setCheckpointTimeout(10 * 60000L);
        //env.getCheckpointConfig().setMaxConcurrentCheckpoints(2);
        //env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 5000L));

        //1.2 设置状态后端
        //env.setStateBackend(new HashMapStateBackend());
        //env.getCheckpointConfig().setCheckpointStorage("hdfs://hadoop102:8020/211126/ck");
        //System.setProperty("HADOOP_USER_NAME", "mumu");

        //1.3 设置状态的TTL  生产环境设置为最大乱序程度
        //tableEnv.getConfig().setIdleStateRetention(Duration.ofSeconds(5));

        //TODO 2.读取DWD层Kafka下单数据主题
        String topic = "dwd_trade_order_detail";
        String groupId = "dws_trade_province_order_window_211126";
        DataStreamSource<String> kafkaDS = env.addSource(MyKafkaUtil.getFlinkKafkaConsumer(topic, groupId));

        //TODO 3.将每行数据转换为JSON对象
        SingleOutputStreamOperator<JSONObject> jsonObjDS = kafkaDS.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) throws Exception {
                try {
                    JSONObject jsonObject = JSON.parseObject(value);
                    out.collect(jsonObject);
                } catch (Exception e) {
                    System.out.println("Value>>>>>>>>" + value);
                }

            }
        });

        //TODO 4.按照订单明细order_detail_id分组、去重(取最后一条)
        KeyedStream<JSONObject, String> keyedByDetailIdDS = jsonObjDS.keyBy(json -> json.getString("id"));

        SingleOutputStreamOperator<JSONObject> filterDS = keyedByDetailIdDS.process(new KeyedProcessFunction<String, JSONObject, JSONObject>() {
            private ValueState<JSONObject> valueState;

            @Override
            public void open(Configuration parameters) throws Exception {
                ValueStateDescriptor<JSONObject> stateDescriptor = new ValueStateDescriptor<JSONObject>("value-state", JSONObject.class);
                valueState = getRuntimeContext().getState(stateDescriptor);
            }

            @Override
            public void processElement(JSONObject value, KeyedProcessFunction<String, JSONObject, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {
                //取出状态中的数据
                JSONObject lastValue = valueState.value();
                //判断状态数据是否为null
                if (lastValue == null) {
                    valueState.update(value);

                    //注册定时器
                    long processingTime = ctx.timerService().currentProcessingTime();
                    ctx.timerService().registerProcessingTimeTimer(processingTime + 5000L);
                } else {
                    //取出状态数据以及当前数据中的时间字段
                    String lastTs = lastValue.getString("row_op_ts");
                    String curTs = value.getString("row_op_ts");

                    if (TimestampLtz3CompareUtil.compare(lastTs, curTs) != 1) {
                        valueState.update(value);
                    }

                }
            }

            @Override
            public void onTimer(long timestamp, KeyedProcessFunction<String, JSONObject, JSONObject>.OnTimerContext ctx, Collector<JSONObject> out) throws Exception {
                //输出数据并清空状态
                out.collect(valueState.value());
                valueState.clear();
            }
        });

        //TODO 5.将每行数据转换为JavaBean
        SingleOutputStreamOperator<TradeProvinceOrderWindow> tradeProvinceOrderDS = filterDS.map(line -> {
            HashSet<String> orderIds = new HashSet<>();
            orderIds.add(line.getString("order_id"));

            return TradeProvinceOrderWindow.builder()
                    .provinceId(line.getString("province_id"))
                    .orderIdSet(orderIds)
                    .orderAmount(line.getDouble("split_total_amount"))
                    .ts(DateFormatUtil.toTs(line.getString("create_time"),true))
                    .build();
        });
        tradeProvinceOrderDS.print("tradeProvinceOrderDS>>>>>>>>>>");

        //TODO 6.提取时间戳生成Watermark
        SingleOutputStreamOperator<TradeProvinceOrderWindow> tradeProvinceOrderWithWmDS = tradeProvinceOrderDS.assignTimestampsAndWatermarks(WatermarkStrategy.<TradeProvinceOrderWindow>forBoundedOutOfOrderness(Duration.ofSeconds(2)).withTimestampAssigner(new SerializableTimestampAssigner<TradeProvinceOrderWindow>() {
            @Override
            public long extractTimestamp(TradeProvinceOrderWindow element, long recordTimestamp) {
                return element.getTs();
            }
        }));
        //tradeProvinceOrderDS.print("tradeProvinceOrderWithWmDS>>>>>>>>>>");
        //TODO 7.分组开窗聚合
        KeyedStream<TradeProvinceOrderWindow, Tuple1<String>> keyedStream = tradeProvinceOrderWithWmDS.keyBy(new KeySelector<TradeProvinceOrderWindow, Tuple1<String>>() {
            @Override
            public Tuple1<String> getKey(TradeProvinceOrderWindow value) throws Exception {
                return new Tuple1<>(value.getProvinceId());
            }
        });
        //tradeProvinceOrderDS.print("keyedStream>>>>>>>>>>");

        SingleOutputStreamOperator<TradeProvinceOrderWindow> reduceDS = keyedStream.window(TumblingEventTimeWindows.of(Time.seconds(10))).reduce(
                new ReduceFunction<TradeProvinceOrderWindow>() {
                    @Override
                    public TradeProvinceOrderWindow reduce(TradeProvinceOrderWindow value1, TradeProvinceOrderWindow value2) throws Exception {
                        value1.getOrderIdSet().addAll(value2.getOrderIdSet());
                        value1.setOrderAmount(value1.getOrderAmount() + value2.getOrderAmount());
                        return value1;
                    }
                }, new WindowFunction<TradeProvinceOrderWindow, TradeProvinceOrderWindow, Tuple1<String>, TimeWindow>() {
                    @Override
                    public void apply(Tuple1<String> stringTuple1, TimeWindow window, Iterable<TradeProvinceOrderWindow> input, Collector<TradeProvinceOrderWindow> out) throws Exception {

                        TradeProvinceOrderWindow provinceOrderWindowBean = input.iterator().next();

                        provinceOrderWindowBean.setTs(System.currentTimeMillis());
                        provinceOrderWindowBean.setOrderCount((long) provinceOrderWindowBean.getOrderIdSet().size());
                        provinceOrderWindowBean.setStt(DateFormatUtil.toYmdHms(window.getStart()));
                        provinceOrderWindowBean.setEdt(DateFormatUtil.toYmdHms(window.getEnd()));

                        out.collect(provinceOrderWindowBean);
                    }
                }
        );
        reduceDS.print("reduceDS>>>>>>>>>>");

        //TODO 8.关联省份维表补充省份名称字段
        SingleOutputStreamOperator<TradeProvinceOrderWindow> reduceWithProvinceNameDS = AsyncDataStream.unorderedWait(reduceDS,
                new DimAsyncFunction<TradeProvinceOrderWindow>("DIM_BASE_PROVINCE") {
                    @Override
                    public String getKey(TradeProvinceOrderWindow input) {
                        return input.getProvinceId();
                    }

                    @Override
                    public void join(TradeProvinceOrderWindow input, JSONObject dimInfo) {
                        input.setProvinceName(dimInfo.getString("NAME"));
                    }
                }, 100, TimeUnit.SECONDS);

        //TODO 9.将数据写出到ClickHouse
        reduceWithProvinceNameDS.print("reduceWithProvinceNameDS>>>>>>>>>>");
        reduceWithProvinceNameDS.addSink(MyClickHouseUtil.getSinkFunction("insert into dws_trade_province_order_window values(?,?,?,?,?,?,?)"));

        //TODO 10.启动任务
        env.execute("DwsTradeProvinceOrderWindow");

    }
}
