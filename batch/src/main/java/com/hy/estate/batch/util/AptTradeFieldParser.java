package com.hy.estate.batch.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public class AptTradeFieldParser {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("uu.MM.dd")
            .withResolverStyle(ResolverStyle.STRICT);

    public static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.strip();
    }

    public static Integer parseFloor(String raw) {
        String floor = blankToNull(raw);
        if (floor == null) return null;

        return Integer.parseInt(floor);
    }

    public static boolean parseCancelled(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        } else if ("O".equals(raw.strip())) {
            return true;
        } else {
            throw new IllegalArgumentException("해제여부 값이 올바르지 않습니다 : '" + raw + "'");
        }
    }

    public static BigDecimal parseArea(String raw) {

        if (blankToNull(raw) == null) throw new IllegalArgumentException("전용면적이 올바르지 않습니다 : '" + raw + "'");

        try {
            return new BigDecimal(raw.strip()).setScale(2, RoundingMode.HALF_UP);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("전용면적이 올바르지 않습니다 : '" + raw + "'",e);
        }
    }

    public static LocalDate parseDealDate(String y, String m, String d) {
        String rawY = blankToNull(y);
        String rawM = blankToNull(m);
        String rawD = blankToNull(d);

        try {
            return LocalDate.of(Integer.parseInt(rawY), Integer.parseInt(rawM), Integer.parseInt(rawD));
        } catch (NumberFormatException | DateTimeException e) {
            throw new IllegalArgumentException("계약일이 올바르지 않습니다 : " + y + "/" + m + "/" + d, e);
        }
    }

    public static LocalDate parseShortDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip(), SHORT_DATE);

        } catch (DateTimeException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다 : '" + raw + "'", e);
        }
    }

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
