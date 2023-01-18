package com.mumu.gmall.publisher.service;

import com.mumu.gmall.publisher.bean.CouponReduceStats;

import java.util.List;

public interface CouponStatsService {
    List<CouponReduceStats> getCouponStats(Integer date);
}
