package com.mumu.app.func;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson.JSONObject;
import com.mumu.utils.DimUtil;
import com.mumu.utils.DruidDSUtil;
import com.mumu.utils.PhoenixUtil;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

public class DimSinkFunction extends RichSinkFunction<JSONObject> {

    private DruidDataSource druidDataSource=null;

    @Override
    public void open(Configuration parameters) throws Exception {
        druidDataSource = DruidDSUtil.createDataSource();
    }

    //{"before":null,"after":{"source_table":"qq","sink_table":"qqq","sink_columns":"qq","sink_pk":null,"sink_extend":null},"source":{"version":"1.5.4.Final","connector":"mysql","name":"mysql_binlog_source","ts_ms":1671644680849,"snapshot":"false","db":"gmall_config","sequence":null,"table":"table_process","server_id":0,"gtid":null,"file":"","pos":0,"row":0,"thread":null,"query":null},"op":"r","ts_ms":1671644680851,"transaction":null}

    @Override
    public void invoke(JSONObject value, Context context) throws Exception {
        //获取连接
        DruidPooledConnection connection = druidDataSource.getConnection();

        String sinkTable = value.getString("sinkTable");
        JSONObject data = value.getJSONObject("data");

        //获取数据类型
        String type = value.getString("type");
        //如果为更新数据,则需要删除Redis中的数据
        if ("update".equals(type)) {
            DimUtil.delDimInfo(sinkTable.toUpperCase(), data.getString("id"));
        }

        //写出数据
        PhoenixUtil.upsertValues(connection, sinkTable, data);

        //归还连接
        connection.close();
    }
}
