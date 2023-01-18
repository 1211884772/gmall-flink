package com.mumu.gmall.publisher.service;

import com.mumu.gmall.publisher.bean.TrafficKeywords;

import java.util.List;

public interface TrafficKeywordsService {
    List<TrafficKeywords> getKeywords(Integer date);
}
