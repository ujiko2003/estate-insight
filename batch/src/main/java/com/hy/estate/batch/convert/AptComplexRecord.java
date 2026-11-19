package com.hy.estate.batch.convert;

public record AptComplexRecord(
        String aptSeq,
        String aptName,
        String sggCode,
        String umdCode,
        String umdName,
        String jibunAddress,
        String roadAddress,
        Integer buildYear
) {
}
