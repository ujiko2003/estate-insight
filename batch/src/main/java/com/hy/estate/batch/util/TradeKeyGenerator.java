package com.hy.estate.batch.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;

public class TradeKeyGenerator {

    public static String generate(String aptSeq, LocalDate dealDate, Integer floor,
                                  BigDecimal area, int amount, String aptDong) {

        String seq = AptTradeFieldParser.blankToNull(aptSeq);

        if ( seq== null ||
            dealDate == null ||
                area == null
        ) {
            throw new IllegalArgumentException("trade_key 필수 값이 비어있습니다 : " +
                    "aptSeq=" + aptSeq + ", dealDate=" + dealDate + ", area=" + area);
        }

        String areaText = area.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String floorText = Objects.toString(floor,"");
        String dongText = Objects.toString(AptTradeFieldParser.blankToNull(aptDong), "");

        String joined = String.join("|",
                seq,
                dealDate.toString(),
                floorText,
                areaText,
                String.valueOf(amount),
                dongText
                );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.",e);
        }
    }
}
