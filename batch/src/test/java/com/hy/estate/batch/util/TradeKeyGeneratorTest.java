package com.hy.estate.batch.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeKeyGeneratorTest {

    // 실제 응답(gangnam-202609.xml) 첫 건: 11680-205 대치삼성, 2026-09-30, 5층, 97.35㎡, 335,000만원, 동 공백
    private static final String APT_SEQ = "11680-205";
    private static final LocalDate DEAL_DATE = LocalDate.of(2026, 9, 30);
    private static final Integer FLOOR = 5;
    private static final BigDecimal AREA = new BigDecimal("97.35");
    private static final int AMOUNT = 335000;

    private static String keyWith(Integer floor, BigDecimal area, int amount, String aptDong) {
        return TradeKeyGenerator.generate(APT_SEQ, DEAL_DATE, floor, area, amount, aptDong);
    }

    // ---------- 고정값(golden): 키 형식은 바꾸면 안 되는 계약 ----------

    // 기대값은 코드와 독립적으로 셸에서 계산했다.
    // printf '%s' '11680-205|2026-09-30|5|97.35|335000|' | shasum -a 256
    @Test
    void 실제_응답_첫_건의_키는_고정값과_같다() {
        assertThat(keyWith(FLOOR, AREA, AMOUNT, null))
                .isEqualTo("db9b647c8b17faae472e36bccfc7a59fce2f5d9c0dd83207257ff9e00d2b937f");
    }

    // 한글은 UTF-8 바이트로 해시해야 한다. OS 기본 인코딩을 쓰면 환경마다 키가 달라진다.
    // printf '%s' '11680-205|2026-09-30|5|97.35|335000|101동' | shasum -a 256
    @Test
    void 한글_동이_있는_키도_고정값과_같다() {
        assertThat(keyWith(FLOOR, AREA, AMOUNT, "101동"))
                .isEqualTo("b99f54f4bceda7df386036c773afcfa3257e36b5e96755017b2369e80cdcbdb9");
    }

    // 층·동이 null이면 빈 값이 된다 ("null" 문자열이 들어가면 안 된다).
    // printf '%s' '11680-205|2026-09-30||97.35|335000|' | shasum -a 256
    @Test
    void 층과_동이_없는_키도_고정값과_같다() {
        assertThat(keyWith(null, AREA, AMOUNT, null))
                .isEqualTo("97f463b7191b2520c1f11843508c239abe0d3469d10ad02ccb0ac5a8227c2299");
    }

    // ---------- 형식 ----------

    @Test
    void 키는_소문자_16진수_64자다() {
        assertThat(keyWith(FLOOR, AREA, AMOUNT, "101동")).matches("[0-9a-f]{64}");
    }

    // ---------- 같은 거래는 같은 키 ----------

    @Test
    void 동이_null이든_빈_문자열이든_공백이든_같은_키() {
        assertThat(keyWith(FLOOR, AREA, AMOUNT, null))
                .isEqualTo(keyWith(FLOOR, AREA, AMOUNT, ""))
                .isEqualTo(keyWith(FLOOR, AREA, AMOUNT, " "));
    }

    @Test
    void 면적은_표기가_달라도_같은_키() {
        assertThat(keyWith(FLOOR, new BigDecimal("60"), AMOUNT, null))
                .isEqualTo(keyWith(FLOOR, new BigDecimal("60.00"), AMOUNT, null))
                .isEqualTo(keyWith(FLOOR, new BigDecimal("60.0"), AMOUNT, null));
    }

    @Test
    void 층이_null이면_빈_값으로_처리되어_항상_같은_키() {
        assertThat(keyWith(null, AREA, AMOUNT, null))
                .isEqualTo(keyWith(null, AREA, AMOUNT, null))
                .isNotEqualTo(keyWith(0, AREA, AMOUNT, null));
    }

    // ---------- 다른 거래는 다른 키 ----------

    @Test
    void 층만_다르면_다른_키() {
        assertThat(keyWith(5, AREA, AMOUNT, null)).isNotEqualTo(keyWith(6, AREA, AMOUNT, null));
    }

    @Test
    void 금액만_다르면_다른_키() {
        assertThat(keyWith(FLOOR, AREA, 335000, null)).isNotEqualTo(keyWith(FLOOR, AREA, 335001, null));
    }

    @Test
    void 동만_다르면_다른_키() {
        assertThat(keyWith(FLOOR, AREA, AMOUNT, "101동")).isNotEqualTo(keyWith(FLOOR, AREA, AMOUNT, "102동"));
    }

    @Test
    void 단지나_계약일만_달라도_다른_키() {
        String base = keyWith(FLOOR, AREA, AMOUNT, null);

        assertThat(TradeKeyGenerator.generate("11680-206", DEAL_DATE, FLOOR, AREA, AMOUNT, null)).isNotEqualTo(base);
        assertThat(TradeKeyGenerator.generate(APT_SEQ, DEAL_DATE.plusDays(1), FLOOR, AREA, AMOUNT, null)).isNotEqualTo(base);
    }

    // 구분자가 없으면 "1" + "12.00" 과 "11" + "2.00" 이 둘 다 "112.00" 이 된다.
    @Test
    void 구분자가_있어서_이어붙인_결과가_같아도_다른_키() {
        assertThat(keyWith(1, new BigDecimal("12.00"), AMOUNT, null))
                .isNotEqualTo(keyWith(11, new BigDecimal("2.00"), AMOUNT, null));
    }

    // ---------- 필수 값 ----------

    @Test
    void 필수_값이_null이면_예외() {
        assertThatThrownBy(() -> TradeKeyGenerator.generate(null, DEAL_DATE, FLOOR, AREA, AMOUNT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeKeyGenerator.generate(" ", DEAL_DATE, FLOOR, AREA, AMOUNT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeKeyGenerator.generate(APT_SEQ, null, FLOOR, AREA, AMOUNT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeKeyGenerator.generate(APT_SEQ, DEAL_DATE, FLOOR, null, AMOUNT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
