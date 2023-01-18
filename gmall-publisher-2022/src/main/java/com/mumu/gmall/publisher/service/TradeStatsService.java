package com.mumu.gmall.publisher.service;

import com.mumu.gmall.publisher.bean.TradeProvinceOrderAmount;
import com.mumu.gmall.publisher.bean.TradeProvinceOrderCt;
import com.mumu.gmall.publisher.bean.TradeStats;

import java.util.List;

public interface TradeStatsService {
    Double getTotalAmount(Integer date);

    List<TradeStats> getTradeStats(Integer date);

    List<TradeProvinceOrderCt> getTradeProvinceOrderCt(Integer date);

    List<TradeProvinceOrderAmount> getTradeProvinceOrderAmount(Integer date);
}
