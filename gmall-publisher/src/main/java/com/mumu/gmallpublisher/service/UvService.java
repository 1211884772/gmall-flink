package com.mumu.gmallpublisher.service;


import java.util.Map;


public interface UvService {

    //获取Uv数据,按照渠道分组的日活数据
    Map getUvByCh(int date) ;
}
