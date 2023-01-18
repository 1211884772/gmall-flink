package com.mumu.app.func;

import com.mumu.utils.KeywordUtil;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@FunctionHint(output = @DataTypeHint("ROW<word STRING>"))
public  class SplitFunction extends TableFunction<Row> {

    public void eval(String str) {
//        for (String s : str.split(" ")) {
//            // use collect(...) to emit a row
//            collect(Row.of(s, s.length()));
//        }
        List<String> list = null;
        try {
            list = KeywordUtil.splitKeyword(str);
            for (String word : list){
                collect(Row.of(word));
            }
        } catch (IOException e) {
            collect(Row.of(str));
        }
    }
}
