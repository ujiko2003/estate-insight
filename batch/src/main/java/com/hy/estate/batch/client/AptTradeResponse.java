package com.hy.estate.batch.client;


import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

public record AptTradeResponse(Header header, Body body) {

    public record Header(String resultCode, String resultMsg) {

    }

    public record Body(Items items, Integer numOfRows, Integer pageNo, Integer totalCount) {

    }

    public record Items(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "item")
            List<Item> item) {

    }

    public record Item(
            // 단지
            String aptSeq, String aptNm, String buildYear,
            // 위치
            String sggCd, String umdCd, String umdNm, String jibun, String bonbun, String bubun,
            String roadNm, String roadNmBonbun, String roadNmBubun,
            // 거래
            String dealYear, String dealMonth, String dealDay, String dealAmount,
            String excluUseAr, String floor, String aptDong,
            // 거래 유형
            String dealingGbn, String buyerGbn, String slerGbn,
            // 등기, 해제
            String rgstDate, String cdealType, String cdealDay) {
    }


}
