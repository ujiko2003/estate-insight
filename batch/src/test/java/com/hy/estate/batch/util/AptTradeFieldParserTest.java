package com.hy.estate.batch.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
}
