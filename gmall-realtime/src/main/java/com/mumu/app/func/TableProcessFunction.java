package com.mumu.app.func;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mumu.bean.TableProcess;
import com.mumu.common.GmallConfig;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class TableProcessFunction  extends BroadcastProcessFunction<JSONObject, String, JSONObject> {

    private Connection connection;
    private MapStateDescriptor<String, TableProcess> mapStateDescriptor;

    public TableProcessFunction(MapStateDescriptor<String, TableProcess> mapStateDescriptor) {
        this.mapStateDescriptor = mapStateDescriptor;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        connection= DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);
    }

    //{"before":null,"after":{"source_table":"qq","sink_table":"qqq","sink_columns":"qq","sink_pk":null,"sink_extend":null},"source":{"version":"1.5.4.Final","connector":"mysql","name":"mysql_binlog_source","ts_ms":1671644680849,"snapshot":"false","db":"gmall_config","sequence":null,"table":"table_process","server_id":0,"gtid":null,"file":"","pos":0,"row":0,"thread":null,"query":null},"op":"r","ts_ms":1671644680851,"transaction":null}
    @Override
    public void processBroadcastElement(String s, BroadcastProcessFunction<JSONObject, String, JSONObject>.Context context, Collector<JSONObject> collector) throws Exception {
        //TODO ：我新增的建库语句
        //效验并建库
        checkSchema(GmallConfig.HBASE_SCHEMA);

        //1.获取并解析数据
        JSONObject jsonObject= JSON.parseObject(s);
        TableProcess tableProcess = JSON.parseObject(jsonObject.getString("after"), TableProcess.class);
        //2.校验并建表
        checkTable(tableProcess.getSinkTable(),
                tableProcess.getSinkColumns(),
                tableProcess.getSinkPk(),
                tableProcess.getSinkExtend());

        //3.写入状态，广播出去
        BroadcastState<String, TableProcess> broadcastState = context.getBroadcastState(mapStateDescriptor);
        broadcastState.put(tableProcess.getSourceTable(),tableProcess);
    }

    /**
     * 效验并建表
     * create table if not exists db.tn(id varchar primary key,bb varchar,cc varchar) xxx
     * @param sinkTable phoenix表名
     * @param sinkColumns phoenix表字段
     * @param sinkPk phoenix表主键
     * @param sinkExtend phoenix表扩展字段
     */
    private void checkTable(String sinkTable,String sinkColumns,String sinkPk,String sinkExtend){

        PreparedStatement preparedStatement = null;
        try {
            //处理特殊字段
            if(sinkPk==null||"".equals(sinkPk)){
                sinkPk="id";
            }
            if(sinkExtend==null){
                sinkExtend="";
            }
            //拼接sql
            StringBuilder createTableSql = new StringBuilder("create table if not exists ")
                    .append(GmallConfig.HBASE_SCHEMA)
                    .append(".")
                    .append(sinkTable)
                    .append("(");
            String[] columns=sinkColumns.split(",");
            for (int i=0;i<columns.length;i++){
                //取出字段
                String column=columns[i];
                //判断是否为主键
                if(sinkPk.equals(column)){
                    createTableSql.append(column).append(" varchar primary key");
                }else{
                    createTableSql.append(column).append(" varchar");
                }
                //判断是否为最后一个字段
                if(i<columns.length-1){
                    createTableSql.append(",");
                }

            }
            createTableSql.append(")").append(sinkExtend);
            //编译sql
            System.out.println("建表语句："+createTableSql);
            preparedStatement = connection.prepareStatement(createTableSql.toString());

            //执行sql,建表
            preparedStatement.execute();

        } catch (SQLException e) {
            throw new RuntimeException("建表失败："+sinkTable);
        } finally {
            //释放资源
            if(preparedStatement!=null){
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    //CREATE SCHEMA IF NOT EXISTS "GMALL211126_REALTIME";

    /**
     * 创建hbase库
     * @param HbaseSchema  库名
     */
    private void checkSchema(String HbaseSchema){

        PreparedStatement preparedStatement = null;
        try {
            //拼接sql
            StringBuilder createSchemaIfNotExistsSql = new StringBuilder("create schema if not exists ")
                    .append(HbaseSchema);
            //编译sql
            System.out.println("建库语句："+createSchemaIfNotExistsSql);
            preparedStatement = connection.prepareStatement(createSchemaIfNotExistsSql.toString());

            //执行sql,建库
            preparedStatement.execute();

        } catch (SQLException e) {
            throw new RuntimeException("建库失败："+HbaseSchema);
        } finally {
            //释放资源
            if(preparedStatement!=null){
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    @Override
    public void processElement(JSONObject jsonObject, BroadcastProcessFunction<JSONObject, String, JSONObject>.ReadOnlyContext readOnlyContext, Collector<JSONObject> collector) throws Exception {

        //1.获取广播的配置数据
        ReadOnlyBroadcastState<String, TableProcess> broadcastState = readOnlyContext.getBroadcastState(mapStateDescriptor);
        String table = jsonObject.getString("table");
        TableProcess tableProcess = broadcastState.get(table);

        String type = jsonObject.getString("type");
        if(tableProcess!=null){
            //2.过滤字段
            filterColumn(jsonObject.getJSONObject("data"),tableProcess.getSinkColumns());

            //3.补充sinkTable并写出到流中
            jsonObject.put("sinkTable",tableProcess.getSinkTable());
            collector.collect(jsonObject);

        }else {
            //System.out.println("过滤掉："+jsonObject);
            System.out.println("找不到对应的Key:"+table);
        }

    }

    /**
     * 过滤字段
     * @param data   {"id":13,"tm_name":"atguigu"}
     * @param sinkColumns “id,tm_name"
     */
    private void filterColumn(JSONObject data, String sinkColumns) {
        //切分
        String[] columns = sinkColumns.split(",");
        List<String> columnList = Arrays.asList(columns);

//        Set<Map.Entry<String, Object>> entries = data.entrySet();
//        Iterator<Map.Entry<String, Object>> iterator = entries.iterator();
//        while(iterator.hasNext()){
//            Map.Entry<String, Object> next = iterator.next();
//            if(!columnList.contains(next.getKey())){
//                iterator.remove();
//            }
//        }

        Set<Map.Entry<String, Object>> entries = data.entrySet();
        entries.removeIf(next->!columnList.contains(next.getKey()));
    }


}
