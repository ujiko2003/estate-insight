package com.hy.estate.batch.convert;

import com.hy.estate.batch.client.AptTradePage;
import com.hy.estate.batch.client.AptTradeResponse;
import com.hy.estate.batch.client.AptTradeXmlParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AptTradeItemConverterTest {

    private static List<AptTradeResponse.Item> realItems() throws IOException {
        try (InputStream in = AptTradeItemConverterTest.class.getResourceAsStream("/apt-trade/gangnam-202609.xml")) {
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            AptTradePage page = new AptTradeXmlParser().parse(xml);
            return page.items();
        }
    }

    // 실제 응답 첫 건(대치삼성)을 기준으로, 바꾸고 싶은 필드만 바꿔 쓴다.
    private static AptTradeResponse.Item item(String aptSeq, String aptNm, String buildYear, String sggCd,
                                              String umdNm, String jibun,
                                              String roadNm, String roadNmBonbun, String roadNmBubun) {
        return new AptTradeResponse.Item(
                aptSeq, aptNm, buildYear,
                sggCd, "10600", umdNm, jibun, "1014", "0003",
                roadNm, roadNmBonbun, roadNmBubun,
                "2026", "9", "30", "335,000",
                "97.35", "5", " ",
                "중개거래", "개인", "개인",
                " ", " ", " ");
    }

    private static AptTradeResponse.Item firstItem() {
        return item("11680-205", "대치삼성", "2000", "11680", "대치동", "1014-3", "도곡로78길", "00022", "00000");
    }

    // ---------- 실제 응답 125건 (브리프 1-6-3 DoD 2·3) ----------

    @Test
    void 실제_응답_125건이_모두_변환되고_키가_모두_다르다() throws IOException {
        List<AptTradeRecord> trades = realItems().stream().map(AptTradeItemConverter::toTrade).toList();

        assertThat(trades).hasSize(125);
        assertThat(trades).extracting(AptTradeRecord::tradeKey)
                .doesNotHaveDuplicates()
                .allMatch(key -> key.matches("[0-9a-f]{64}"));
    }

    @Test
    void 실제_응답의_서로_다른_단지는_80개다() throws IOException {
        List<AptComplexRecord> complexes = realItems().stream().map(AptTradeItemConverter::toComplex).toList();

        assertThat(complexes).hasSize(125);
        // record의 equals 덕분에 같은 단지는 하나로 합쳐진다. 5단계 Writer가 단지를 먼저 upsert할 때 쓰는 방식.
        assertThat(complexes.stream().distinct().count()).isEqualTo(80);
        assertThat(complexes.stream().map(AptComplexRecord::aptSeq).distinct().count()).isEqualTo(80);
    }

    @Test
    void 실제_응답의_해제_거래는_1건이다() throws IOException {
        List<AptTradeRecord> cancelled = realItems().stream()
                .map(AptTradeItemConverter::toTrade)
                .filter(AptTradeRecord::cancelled)
                .toList();

        assertThat(cancelled).hasSize(1);
        AptTradeRecord trade = cancelled.get(0);
        assertThat(trade.aptSeq()).isEqualTo("11680-290");
        assertThat(trade.dealDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(trade.cancelledDate()).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    void 실제_응답의_동_없음은_86건_등기일_있음은_45건이다() throws IOException {
        List<AptTradeRecord> trades = realItems().stream().map(AptTradeItemConverter::toTrade).toList();

        assertThat(trades).filteredOn(t -> t.aptDong() == null).hasSize(86);
        assertThat(trades).filteredOn(t -> t.registeredDate() != null).hasSize(45);
    }

    // ---------- 첫 건 필드 매핑 ----------

    @Test
    void 첫_건의_단지_필드가_제자리에_들어간다() {
        AptComplexRecord complex = AptTradeItemConverter.toComplex(firstItem());

        assertThat(complex).isEqualTo(new AptComplexRecord(
                "11680-205", "대치삼성", "11680", "10600", "대치동",
                "대치동 1014-3", "도곡로78길 22", 2000));
    }

    @Test
    void 첫_건의_거래_필드가_제자리에_들어간다() {
        AptTradeRecord trade = AptTradeItemConverter.toTrade(firstItem());

        // 키는 TradeKeyGeneratorTest의 고정값과 같아야 한다 (같은 입력)
        assertThat(trade).isEqualTo(new AptTradeRecord(
                "db9b647c8b17faae472e36bccfc7a59fce2f5d9c0dd83207257ff9e00d2b937f",
                "11680-205", "11680", LocalDate.of(2026, 9, 30), 335000, new BigDecimal("97.35"),
                5, null, "중개거래", "개인", "개인", null, false, null));
    }

    // ---------- 주소 조립 (결정 31) ----------

    @Test
    void 도로명_부번이_0이면_생략한다() {
        assertThat(AptTradeItemConverter.toComplex(firstItem()).roadAddress()).isEqualTo("도곡로78길 22");
    }

    @Test
    void 도로명_부번이_있으면_하이픈으로_붙인다() {
        AptTradeResponse.Item item = item("11680-205", "대치삼성", "2000", "11680", "대치동", "1014-3",
                "도곡로78길", "00022", "00003");

        assertThat(AptTradeItemConverter.toComplex(item).roadAddress()).isEqualTo("도곡로78길 22-3");
    }

    @Test
    void 도로명이나_본번이_비면_도로명_주소는_null() {
        AptTradeResponse.Item noRoad = item("11680-205", "대치삼성", "2000", "11680", "대치동", "1014-3",
                " ", "00022", "00000");
        AptTradeResponse.Item noBonbun = item("11680-205", "대치삼성", "2000", "11680", "대치동", "1014-3",
                "도곡로78길", " ", " ");

        assertThat(AptTradeItemConverter.toComplex(noRoad).roadAddress()).isNull();
        assertThat(AptTradeItemConverter.toComplex(noBonbun).roadAddress()).isNull();
    }

    @Test
    void 법정동이나_지번이_비면_지번_주소는_null() {
        AptTradeResponse.Item noUmd = item("11680-205", "대치삼성", "2000", "11680", " ", "1014-3",
                "도곡로78길", "00022", "00000");
        AptTradeResponse.Item noJibun = item("11680-205", "대치삼성", "2000", "11680", "대치동", " ",
                "도곡로78길", "00022", "00000");

        assertThat(AptTradeItemConverter.toComplex(noUmd).jibunAddress()).isNull();
        assertThat(AptTradeItemConverter.toComplex(noJibun).jibunAddress()).isNull();
    }

    // ---------- 비어도 되는 값과 안 되는 값 ----------

    @Test
    void 건축년도가_비면_null() {
        AptTradeResponse.Item item = item("11680-205", "대치삼성", " ", "11680", "대치동", "1014-3",
                "도곡로78길", "00022", "00000");

        assertThat(AptTradeItemConverter.toComplex(item).buildYear()).isNull();
    }

    @Test
    void 단지_필수_값이_비면_예외() {
        AptTradeResponse.Item noSeq = item(" ", "대치삼성", "2000", "11680", "대치동", "1014-3", "도곡로78길", "00022", "00000");
        AptTradeResponse.Item noName = item("11680-205", " ", "2000", "11680", "대치동", "1014-3", "도곡로78길", "00022", "00000");
        AptTradeResponse.Item noSgg = item("11680-205", "대치삼성", "2000", " ", "대치동", "1014-3", "도곡로78길", "00022", "00000");

        assertThatThrownBy(() -> AptTradeItemConverter.toComplex(noSeq)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AptTradeItemConverter.toComplex(noName)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AptTradeItemConverter.toComplex(noSgg)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 거래_필수_값이_비면_예외() {
        AptTradeResponse.Item noSeq = item(" ", "대치삼성", "2000", "11680", "대치동", "1014-3", "도곡로78길", "00022", "00000");
        AptTradeResponse.Item noSgg = item("11680-205", "대치삼성", "2000", " ", "대치동", "1014-3", "도곡로78길", "00022", "00000");

        assertThatThrownBy(() -> AptTradeItemConverter.toTrade(noSeq)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AptTradeItemConverter.toTrade(noSgg)).isInstanceOf(IllegalArgumentException.class);
    }
}
