package com.hy.estate.batch.job;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


@Component
@StepScope
public class RawKeyWriter implements ItemWriter<RawSaveResult> {

    private static final String UPDATE_SQL = """
            UPDATE ingest_status
            SET raw_s3_key = ?
            WHERE sgg_code = ? AND deal_ym = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RawKeyWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends RawSaveResult> chunk) throws Exception {
        for (RawSaveResult result : chunk) {
            jdbcTemplate.update(UPDATE_SQL,result.rawKey(),result.sggCode(),result.dealYm());
        }
    }
}
