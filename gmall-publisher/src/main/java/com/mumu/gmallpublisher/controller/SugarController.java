package com.mumu.gmallpublisher.controller;

import com.mumu.gmallpublisher.service.GmvService;
import com.mumu.gmallpublisher.service.UvService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Map;
import java.util.Set;


//@Controller
@RestController //@Controller+@ResponseBody
@RequestMapping("/api/sugar")
public class SugarController {

    //注入service
    @Autowired
    private GmvService gmvService;
    @Autowired
    private UvService uvService;

    @RequestMapping("/test1")
    //@ResponseBody
    public String test1(){
        System.out.println("aaaaaaaaaa");
        //return "index.html";
        return "{\"id\":121,\"name\":\"mumu\"}";
    }

    @RequestMapping("/test2")
    public String test2(@RequestParam("nn") String name, @RequestParam(value = "age",defaultValue = "18") int age ){
        System.out.println(name +":"+ age);
        return "success";
    }

    @RequestMapping("/gmv")
    public String getGmv(@RequestParam(value = "date" ,defaultValue = "0") int date){
        //date=20200526
        //获取当天日期
        if(date==0){
            date = this.getToday();
        }
        //查询数据
        Double gmv = gmvService.getGmv(date);

        //返回结果
        return "{" +
                "\"status\":0," +
                "\"msg\":\"\"," +
                "\"data\":"+gmv+
                "}";
    }


    @RequestMapping("/ch")
    public String getUvByCh(@RequestParam(value = "date",defaultValue = "0") int date){
        //date=20200526
        //获取当天日期
        if(date==0){
            date = this.getToday();
        }
        //查询数据
        Map uvByCh = uvService.getUvByCh(date);
        Set chs = uvByCh.keySet();
        Collection uvs = uvByCh.values();
        //拼接Json字符串
        //返回结果
        return "{" +
                "\"status\": 0," +
                "\"msg\": \"\"," +
                "\"data\": {" +
                "\"categories\": [\"" +
                StringUtils.join(chs,"\",\"")+
                "\"]," +
                "        \"series\": [" +
                "        {" +
                "            \"name\": \"日活\"," +
                "            \"data\": [" +
                StringUtils.join(uvs,",")+
                "]" +
                "        }" +
                "    ]" +
                "    }" +
                "}";
    }


    private int getToday(){
        long ts = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        return Integer.parseInt(sdf.format(ts));
    }

}
