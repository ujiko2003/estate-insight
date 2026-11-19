package com.hy.estate.batch.job;

import com.hy.estate.batch.client.AptTradeApiClient;
import com.hy.estate.batch.client.AptTradeXmlParser;
import com.hy.estate.batch.store.RawResponseStore;
import com.hy.estate.batch.util.CollectTarget;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 API를 부르지 않는다. 응답을 정해 둔 가짜 클라이언트와, 메모리에 저장하는 가짜 저장소를 쓴다.
class FetchRawProcessorTest {

    private static final CollectTarget TARGET = new CollectTarget("11680", "202609");

    /** 호출을 기록하고 정해진 응답을 순서대로 돌려주는 가짜 API 클라이언트 */
    private static class FakeApiClient extends AptTradeApiClient {
        private final List<String> responses;
        private final List<Integer> calledPages = new ArrayList<>();

        FakeApiClient(String... responses) {
            super(RestClient.builder(), "http://unused", "unused");
            this.responses = List.of(responses);
        }

        @Override
        public String fetchPage(String lawdCd, String dealYmd, int pageNo, int pageSize) {
            calledPages.add(pageNo);
            return responses.get(pageNo - 1);
        }
    }

    /** 저장 내용을 메모리에 남기는 가짜 저장소 */
    private static class MemoryStore implements RawResponseStore {
        private final Map<String, String> saved = new LinkedHashMap<>();

        @Override
        public void save(String key, String content) {
            saved.put(key, content);
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = FetchRawProcessorTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String page(int pageNo, int totalCount) {
        return """
                <response><header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                <body><items/><numOfRows>1000</numOfRows><pageNo>%d</pageNo><totalCount>%d</totalCount></body></response>
                """.formatted(pageNo, totalCount);
    }

    @Test
    void 한_페이지에_다_들어오면_한_번만_호출하고_원본을_저장한다() throws IOException {
        String xml = readResource("/apt-trade/gangnam-202609.xml");   // totalCount 125
        FakeApiClient api = new FakeApiClient(xml);
        MemoryStore store = new MemoryStore();

        RawSaveResult result = new FetchRawProcessor(api, new AptTradeXmlParser(), store, "2026-11-21").process(TARGET);

        // totalCount를 페이지 수로 쓰면 125번 호출한다 (실제로 겪은 버그)
        assertThat(api.calledPages).containsExactly(1);
        assertThat(store.saved).containsOnlyKeys(
                "apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-21/page-001.xml");
        assertThat(store.saved.values()).containsExactly(xml);
        assertThat(result).isEqualTo(new RawSaveResult(
                "11680", "202609", "apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-21/"));
    }

    @Test
    void 거래가_없는_달도_한_번_호출하고_저장한다() throws IOException {
        FakeApiClient api = new FakeApiClient(readResource("/apt-trade/gangnam-202612-empty.xml"));
        MemoryStore store = new MemoryStore();

        new FetchRawProcessor(api, new AptTradeXmlParser(), store, "2026-11-21").process(TARGET);

        assertThat(api.calledPages).containsExactly(1);
        assertThat(store.saved).hasSize(1);
    }

    @Test
    void 페이지_크기를_넘으면_다음_페이지까지_받는다() {
        // 1000건씩, 전체 2500건 → 3페이지
        FakeApiClient api = new FakeApiClient(page(1, 2500), page(2, 2500), page(3, 2500));
        MemoryStore store = new MemoryStore();

        new FetchRawProcessor(api, new AptTradeXmlParser(), store, "2026-11-21").process(TARGET);

        assertThat(api.calledPages).containsExactly(1, 2, 3);
        assertThat(store.saved.keySet()).extracting(key -> key.substring(key.lastIndexOf('/') + 1))
                .containsExactly("page-001.xml", "page-002.xml", "page-003.xml");
    }

    // 결정 39: 파싱 전에 저장하므로 오류 응답도 원본으로 남는다
    @Test
    void 오류_응답도_파싱_전에_저장되고_예외가_난다() {
        String errorXml = """
                <response><header><resultCode>999</resultCode><resultMsg>ERROR</resultMsg></header></response>
                """;
        FakeApiClient api = new FakeApiClient(errorXml);
        MemoryStore store = new MemoryStore();
        FetchRawProcessor processor = new FetchRawProcessor(api, new AptTradeXmlParser(), store, "2026-11-21");

        assertThatThrownBy(() -> processor.process(TARGET)).isInstanceOf(IllegalStateException.class);
        assertThat(store.saved.values()).containsExactly(errorXml);
    }
}
