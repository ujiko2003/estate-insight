package com.hy.estate.batch.convert;

import com.hy.estate.batch.client.AptTradeResponse;
import com.hy.estate.batch.util.AptTradeFieldParser;
import com.hy.estate.batch.util.TradeKeyGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AptTradeItemConverter {

    private static String roadAddress(String roadNm, String roadNmBonbun, String roadNmBubun) {
        String name = AptTradeFieldParser.blankToNull(roadNm);
        String bonbun = AptTradeFieldParser.blankToNull(roadNmBonbun);

        if (name == null || bonbun == null) return null;

        String bubun = AptTradeFieldParser.blankToNull(roadNmBubun);

        String address = name + " " + Integer.parseInt(bonbun);
        if (bubun != null && Integer.parseInt(bubun) != 0) {
            address = address + "-" + Integer.parseInt(bubun);     // "00003" → 3
        }
        return address;
    }

    private static String jibunAddress(String umdNm, String jibun) {
        String name = AptTradeFieldParser.blankToNull(umdNm);
        String address = AptTradeFieldParser.blankToNull(jibun);
        if (name == null || address == null) return null;
        return name + " " + address;
    }

    private static String requireText(String raw, String fieldName) {
        String value = AptTradeFieldParser.blankToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "이(가) 비어있습니다 : '" + raw + "'");
        }
        return value;
    }

    public static AptComplexRecord toComplex(AptTradeResponse.Item item) {
        String aptSeq = requireText(item.aptSeq(),"aptSeq");
        String aptName = requireText(item.aptNm(),"aptName");
        String sggCode = requireText(item.sggCd(),"sggCode");
        String umdCode = AptTradeFieldParser.blankToNull(item.umdCd());
        String umdName = AptTradeFieldParser.blankToNull(item.umdNm());
        String jibunAddress = jibunAddress(item.umdNm(), item.jibun());
        String roadAddress = roadAddress(item.roadNm(), item.roadNmBonbun(), item.roadNmBubun());
        Integer buildYear = AptTradeFieldParser.parseFloor(item.buildYear());

        return new AptComplexRecord(aptSeq, aptName, sggCode, umdCode, umdName, jibunAddress, roadAddress, buildYear);
    }

    public static AptTradeRecord toTrade(AptTradeResponse.Item item) {
        String aptSeq = requireText(item.aptSeq(),"aptSeq");
        String sggCode = requireText(item.sggCd(),"sggCode");
        LocalDate dealDate = AptTradeFieldParser.parseDealDate(item.dealYear(),item.dealMonth(),item.dealDay());
        Integer dealAmount = AptTradeFieldParser.parseDealAmount(item.dealAmount());
        BigDecimal exclusiveArea = AptTradeFieldParser.parseArea(item.excluUseAr());
        Integer floor = AptTradeFieldParser.parseFloor(item.floor());
        String aptDong = AptTradeFieldParser.blankToNull(item.aptDong());
        String dealingType = AptTradeFieldParser.blankToNull(item.dealingGbn());
        String buyerType = AptTradeFieldParser.blankToNull(item.buyerGbn());
        String sellerType = AptTradeFieldParser.blankToNull(item.slerGbn());
        LocalDate registeredDate = AptTradeFieldParser.parseShortDate(item.rgstDate());
        boolean cancelled = AptTradeFieldParser.parseCancelled(item.cdealType());
        LocalDate cancelledDate = AptTradeFieldParser.parseShortDate(item.cdealDay());
        String tradeKey = TradeKeyGenerator.generate(aptSeq,dealDate,floor,exclusiveArea,dealAmount,aptDong);


        return new AptTradeRecord(tradeKey, aptSeq, sggCode, dealDate, dealAmount, exclusiveArea, floor, aptDong, dealingType, buyerType, sellerType, registeredDate, cancelled, cancelledDate);
    }
}
