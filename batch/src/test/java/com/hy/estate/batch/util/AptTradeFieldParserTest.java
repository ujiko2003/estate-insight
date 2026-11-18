package com.hy.estate.batch.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class AptTradeFieldParserTest {

    @ParameterizedTest
    @CsvSource({
            "'335,000', 335000",
            "'23,000', 23000",
            "'5,000', 5000",
            "'900', 900",
            "'  82,500 ', 82500",
            "'1,234,567', 1234567"
    })
    void 정상_금액은_정수로_바뀐다(String raw, int expected) {
        assertThat(AptTradeFieldParser.parseDealAmount(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "abc", "0", "-5,000"})
    void 비었거나_잘못된_금액은_예외(String raw) {
        assertThatThrownBy(() -> AptTradeFieldParser.parseDealAmount(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   " })
    void 공백뿐이면_null(String raw) {
        assertThat(AptTradeFieldParser.blankToNull(raw)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "' 101동 ','101동'",
            "'101동', 101동",
            "'중개거래', 중개거래"
    })
    void 값이_있으면_앞뒤_공백만_제거(String raw, String expected) {
        assertThat(AptTradeFieldParser.blankToNull(raw)).isEqualTo(expected);
    }

    // ---------- 층 ----------

    @ParameterizedTest
    @CsvSource({
            "'5', 5",
            "' 12 ', 12",
            "'-1', -1"
    })
    void 정상_층은_정수로_바뀐다(String raw, int expected) {
        assertThat(AptTradeFieldParser.parseFloor(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   " })
    void 층이_비었으면_null(String raw) {
        assertThat(AptTradeFieldParser.parseFloor(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc", "1.5" })
    void 잘못된_층은_예외(String raw) {
        assertThatThrownBy(() -> AptTradeFieldParser.parseFloor(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 해제 여부 ----------

    @ParameterizedTest
    @ValueSource(strings = { "O", " O " })
    void 해제_표시_O는_true(String raw) {
        assertThat(AptTradeFieldParser.parseCancelled(raw)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    void 해제_표시가_비었으면_false(String raw) {
        assertThat(AptTradeFieldParser.parseCancelled(raw)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "X", "0", "o" })   // "0"은 숫자 영, "o"는 소문자
    void 잘못된_해제_표시는_예외(String raw) {
        assertThatThrownBy(() -> AptTradeFieldParser.parseCancelled(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 전용면적 ----------

    // equals로 비교해야 scale(소수 자릿수)까지 확인된다. isEqualByComparingTo는 60과 60.00을 같다고 본다.
    @ParameterizedTest
    @CsvSource({
            "'60', 60.00",
            "'84.93', 84.93",
            "'39.6', 39.60",
            "' 59.98 ', 59.98"
    })
    void 정상_면적은_소수_둘째자리로_맞춘다(String raw, BigDecimal expected) {
        BigDecimal actual = AptTradeFieldParser.parseArea(raw);

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "abc", "84.935" })
    void 비었거나_잘못된_면적은_예외(String raw) {
        assertThatThrownBy(() -> AptTradeFieldParser.parseArea(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 계약일 ----------

    @ParameterizedTest
    @CsvSource({
            "2026, 9, 30, 2026-09-30",
            "2026, 1, 5, 2026-01-05",
            "'2026 ', ' 12', '31', 2026-12-31",
            "2024, 2, 29, 2024-02-29"   // 윤년
    })
    void 정상_계약일은_날짜로_바뀐다(String y, String m, String d, LocalDate expected) {
        assertThat(AptTradeFieldParser.parseDealDate(y, m, d)).isEqualTo(expected);
    }

    // CsvSource에서 따옴표 없는 빈 칸은 null, ' '는 공백 한 칸
    @ParameterizedTest
    @CsvSource({
            "' ', 9, 30",
            "2026, ' ', 30",
            "2026, 9, ' '",
            "2026, , 30",
            "2026, 2, 30",    // 2월 30일
            "2026, 13, 1",    // 13월
            "2025, 2, 29",    // 평년의 2월 29일
            "2026, abc, 1"
    })
    void 비었거나_잘못된_계약일은_예외(String y, String m, String d) {
        assertThatThrownBy(() -> AptTradeFieldParser.parseDealDate(y, m, d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 등기일·해제일 (yy.MM.dd) ----------

    @ParameterizedTest
    @CsvSource({
            "'26.09.21', 2026-09-21",
            "' 26.10.02 ', 2026-10-02",
            "'24.02.29', 2024-02-29"   // 윤년
    })
    void 정상_짧은_날짜는_날짜로_바뀐다(String raw, LocalDate expected) {
        assertThat(AptTradeFieldParser.parseShortDate(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    void 짧은_날짜가_비었으면_null(String raw) {
        assertThat(AptTradeFieldParser.parseShortDate(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "26.13.01", "26.02.30", "25.02.29", "abc", "2026-09-21" })
    void 잘못된_짧은_날짜는_예외(String raw) {
        assertThatThrownBy(() -> AptTradeFieldParser.parseShortDate(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
