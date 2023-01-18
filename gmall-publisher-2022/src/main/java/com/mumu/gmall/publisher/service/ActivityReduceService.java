package com.mumu.gmall.publisher.service;

import com.mumu.gmall.publisher.bean.ActivityReduceStats;

import java.util.List;

public interface ActivityReduceService {
    List<ActivityReduceStats> getActivityStats(Integer date);
}
