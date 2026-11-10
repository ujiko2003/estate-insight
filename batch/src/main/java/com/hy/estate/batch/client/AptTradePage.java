package com.hy.estate.batch.client;

import java.util.List;

public record AptTradePage(int totalCount, List<AptTradeResponse.Item> items) {
}
