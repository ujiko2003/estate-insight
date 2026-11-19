package com.hy.estate.batch.job;

import com.hy.estate.batch.config.CollectProperties;
import com.hy.estate.region.Region;
import com.hy.estate.region.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PrepareTargetsTasklet implements Tasklet {

    private static final String UPSERT_SQL = """
                INSERT INTO ingest_status (sgg_code, deal_ym, status)
                VALUES (?, ?, 'PENDING')
                ON CONFLICT (sgg_code, deal_ym)
                DO UPDATE SET status = 'PENDING', last_error = NULL
                """;

    private final RegionRepository regionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CollectProperties collectProperties;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        //RunDate
        String runDate = chunkContext.getStepContext()
                .getJobParameters()
                .get("runDate")
                .toString();

        //YearMonth
        LocalDate runLocalDate = LocalDate.parse(runDate);
        YearMonth runMonth = YearMonth.from(runLocalDate);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        List<String> targetMonths = new ArrayList<>();

        for(int i = collectProperties.targetMonths()-1; i>=0; i--) {
            YearMonth month = runMonth.minusMonths(i);
            targetMonths.add(month.format(formatter));
        }

        //활성지역조회
        List<Region> activeRegions = regionRepository.findByIsActiveTrue();

        ArrayList<String> collectTargets = new ArrayList<>();
        //지역 x 월마다 Update
        for (Region region: activeRegions) {
            for (String month: targetMonths) {
                jdbcTemplate.update(UPSERT_SQL,region.getSggCode(), month);
                collectTargets.add(region.getSggCode() + "|" + month);
            }
        }

        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("collectTargets", collectTargets);

        return RepeatStatus.FINISHED;
    }
}
