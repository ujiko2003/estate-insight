package com.hy.estate.batch.job;

import com.hy.estate.batch.client.AptTradeApiClient;
import com.hy.estate.batch.client.AptTradePage;
import com.hy.estate.batch.client.AptTradeXmlParser;
import com.hy.estate.batch.store.RawResponseStore;
import com.hy.estate.batch.util.CollectTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class FetchRawProcessor implements ItemProcessor<CollectTarget, RawSaveResult> {

    private static final int PAGE_SIZE = 1000;

    private final AptTradeApiClient apiClient;
    private final AptTradeXmlParser xmlParser;
    private final RawResponseStore rawStore;
    private final String runDate;

    public FetchRawProcessor(
            AptTradeApiClient apiClient,
            AptTradeXmlParser xmlParser,
            RawResponseStore rawStore,
            @Value("#{jobParameters['runDate']}") String runDate) {
        this.apiClient = apiClient;
        this.xmlParser = xmlParser;
        this.rawStore = rawStore;
        this.runDate = runDate;
    }

    @Override
    public RawSaveResult process(CollectTarget target) {
        String dirKey = "apt-trade/sgg=" + target.sggCode()
                + "/deal_ym=" + target.dealYm()
                + "/run_date=" + runDate + "/";

        int pageNo = 1;
        int totalPages = 1;
        while (pageNo <= totalPages) {
            String xml = apiClient.fetchPage(target.sggCode(), target.dealYm(), pageNo, PAGE_SIZE);

            // 파싱 전에 먼저 저장한다 (결정 39). 파싱이 실패해도 원본은 남는다.
            rawStore.save(dirKey + String.format("page-%03d.xml", pageNo), xml);

            AptTradePage page = xmlParser.parse(xml);
            totalPages = Math.max(1, (page.totalCount() + PAGE_SIZE - 1) / PAGE_SIZE);          // totalCount와 PAGE_SIZE로 전체 페이지 수 계산 (최소 1)
            pageNo++;
        }
        return new RawSaveResult(target.sggCode(), target.dealYm(), dirKey);
    }


}
