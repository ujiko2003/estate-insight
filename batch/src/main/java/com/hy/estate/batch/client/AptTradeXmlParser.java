package com.hy.estate.batch.client;

import org.springframework.stereotype.Component;
import tools.jackson.dataformat.xml.XmlMapper;

import java.util.List;

@Component
public class AptTradeXmlParser {

    private final XmlMapper xmlMapper = new XmlMapper();

    public AptTradePage parse(String xml) {
        // 1. 변환 : XML 문자열 -> AptTradeResponse
        AptTradeResponse response = xmlMapper.readValue(xml, AptTradeResponse.class);
        AptTradeResponse.Body body = response.body();
        // 2. 검사 : 정상 응답이 아니면 예외

        if (response.header() == null || !"000".equals(response.header().resultCode())){
            throw new IllegalStateException("조회에 실패하였습니다.");
        }

        if (body == null || body.totalCount() == null){
            throw new IllegalStateException("결과가 존재하지 않습니다.");
        }

        // 3. 정리 : 목록 꺼내기
        List<AptTradeResponse.Item> items;
        if (body.items() == null || body.items().item() == null){
            items = List.of();
        } else {
            items = body.items().item();
        }


        return new AptTradePage(body.totalCount(), items);
    }
}
