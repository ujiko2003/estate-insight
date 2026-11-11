package com.hy.estate.batch.client;


import com.hy.estate.batch.util.AptTradeFieldParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AptTradeXmlParserTest {

    private final AptTradeXmlParser parser = new AptTradeXmlParser();

    private String readResource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void 실제_응답_125건을_모두_읽는다() throws IOException {
        AptTradePage page = parser.parse(readResource("/apt-trade/gangnam-202609.xml"));

        assertThat(page.totalCount()).isEqualTo(125);
        assertThat(page.items()).hasSize(125);
        assertThat(page.items()).allSatisfy(item ->
                        AptTradeFieldParser.parseDealAmount(item.dealAmount()));
    }

    @Test
    void 거래가_없는_달은_빈_목록이다() throws IOException {
        AptTradePage page = parser.parse(readResource("/apt-trade/gangnam-202612-empty.xml"));

        assertThat(page.items()).isEmpty();
    }

    @Test
    void item이_1건이어도_목록으로_읽는다() throws IOException {
        String xml = """
                <response><header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                <body><items><item><aptSeq>11680-205</aptSeq><dealAmount>335,000</dealAmount></item></items>
                <numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>1</totalCount></body></response>
                """;

        AptTradePage page = parser.parse(xml);

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void resultCode가_000이_아니면_예외다() {
        String xml = """
                <response><header><resultCode>999</resultCode><resultMsg>OK</resultMsg></header>
                <body><items><item><aptSeq>11680-205</aptSeq><dealAmount>335,000</dealAmount></item></items>
                <numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>1</totalCount></body></response>
                """;

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(IllegalStateException.class);

    }

    @Test
    void 접근거부_응답은_예외다() {
        String xml = """
                <OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>서비스 접근거부</returnAuthMsg><returnReasonCode>20</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                """;

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(IllegalStateException.class);

    }
}
