package com.hy.estate.batch.util;

public class AptTradeFieldParser {

    public static Integer parseDealAmount(String raw) {

        if(raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("거래금액이 비어있습니다 : '" + raw + "'");
        }

        int amount = Integer.parseInt(raw.strip().replace(",", ""));

        if (amount <= 0) {
            throw new IllegalArgumentException("거래금액이 올바르지않습니다 : '" + raw + "'");
        }
        return amount;
    }
}
