package com.mumu.gmall.publisher.service;

import com.mumu.gmall.publisher.bean.UserChangeCtPerType;
import com.mumu.gmall.publisher.bean.UserPageCt;
import com.mumu.gmall.publisher.bean.UserTradeCt;

import java.util.List;

public interface UserStatsService {
    List<UserPageCt> getUvByPage(Integer date);

    List<UserChangeCtPerType> getUserChangeCt(Integer date);

    List<UserTradeCt> getTradeUserCt(Integer date);
}
