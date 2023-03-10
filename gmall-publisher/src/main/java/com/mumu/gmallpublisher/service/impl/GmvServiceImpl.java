package com.mumu.gmallpublisher.service.impl;

import com.mumu.gmallpublisher.mapper.GmvMapper;
import com.mumu.gmallpublisher.service.GmvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class GmvServiceImpl implements GmvService {

    @Autowired
    private GmvMapper gmvMapper;

    @Override
    public Double getGmv(int date) {
        return gmvMapper.selectGmv(date);
    }
}
