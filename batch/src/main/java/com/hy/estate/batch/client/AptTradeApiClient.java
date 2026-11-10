package com.hy.estate.batch.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class AptTradeApiClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceKey;

    public AptTradeApiClient(RestClient.Builder builder,
                             @Value("${public-data.apt-trade.base-url}") String baseUrl,
                             @Value("${public-data.apt-trade.service-key}") String serviceKey) {
        this.restClient = builder.build();
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public String fetchPage(String lawdCd, String dealYmd, int pageNo, int pageSize){
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/getRTMSDataSvcAptTradeDev")
                .queryParam("serviceKey",serviceKey)
                .queryParam("LAWD_CD",lawdCd)
                .queryParam("DEAL_YMD",dealYmd)
                .queryParam("pageNo",pageNo)
                .queryParam("numOfRows",pageSize)
                .build(true)
                .toUri();

        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
    }

}
