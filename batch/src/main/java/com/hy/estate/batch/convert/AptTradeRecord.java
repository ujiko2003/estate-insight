package com.hy.estate.batch.convert;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AptTradeRecord(
        String tradeKey,
        String aptSeq,
        String sggCode,
        LocalDate dealDate,
        Integer dealAmount,
        BigDecimal exclusiveArea,
        Integer floor,
        String aptDong,
        String dealingType,
        String buyerType,
        String sellerType,
        LocalDate registeredDate,
        boolean cancelled,
        LocalDate cancelledDate
) {
}
