package com.mumu.gmallpublisher.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface UvMapper {

    /**
     * 根据渠道分组，获取当日的日活数据
     * List{
     * Map[(ch->Appstore),(uv->465),...],
     * Map[(ch->Appstore),(uv->465),...],
     * ...
     * }
     * @param date
     * @return List<Map>
     */
    @Select("select ch,sum(uv_ct) uv from dws_traffic_vc_ch_ar_is_new_page_view_window where toYYYYMMDD(stt)=#{date} group by ch order by uv desc ;")
    List<Map> selectUvByCh(int date) ;
}
