package com.mumu.gmall.publisher.controller;

import com.mumu.gmall.publisher.bean.CouponReduceStats;
import com.mumu.gmall.publisher.service.CouponStatsService;
import com.mumu.gmall.publisher.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("gmall/realtime/coupon")
public class CouponStatsController {

    @Autowired
    private CouponStatsService couponStatsService;

    @RequestMapping("/stats")
    public String getCouponStats(
            @RequestParam(value = "date", defaultValue = "1") Integer date){
        if(date == 1) {
            date = DateUtil.now();
        }
        List<CouponReduceStats> couponStatsList = couponStatsService.getCouponStats(date);
        if(couponStatsList == null) {
            return "";
        }
        StringBuilder rows = new StringBuilder("[");
        for (int i = 0; i < couponStatsList.size(); i++) {
            CouponReduceStats couponReduceStats = couponStatsList.get(i);
            Double couponReduceAmount = couponReduceStats.getCouponReduceAmount();
            Double originTotalAmount = couponReduceStats.getOriginTotalAmount();
            Double couponSubsidyRate = couponReduceStats.getCouponSubsidyRate();
            rows.append("{\n" +
                    "        \"couponReduceAmount\": "+ couponReduceAmount +",\n" +
                    "        \"originTotalAmount\": "+ originTotalAmount +",\n" +
                    "        \"couponSubsidyRate\": "+ couponSubsidyRate +"\n" +
                    "      }");
            if(i < couponStatsList.size() - 1) {
                rows.append(",");
            } else {
                rows.append("]");
            }
        }

        return "{\n" +
                "  \"status\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": {\n" +
                "    \"columns\": [\n" +
                "      {\n" +
                "        \"name\": \"?????????????????????\",\n" +
                "        \"id\": \"couponReduceAmount\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"??????????????????\",\n" +
                "        \"id\": \"originTotalAmount\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"??????????????????\",\n" +
                "        \"id\": \"couponSubsidyRate\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"rows\": "+ rows +"\n" +
                "  }\n" +
                "}";
    }
}
