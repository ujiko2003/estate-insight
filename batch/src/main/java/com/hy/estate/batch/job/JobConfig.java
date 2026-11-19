package com.hy.estate.batch.job;

import com.hy.estate.batch.util.CollectTarget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class JobConfig {

    @Bean
    public Job aptTradeDataJob(
            JobRepository jobRepository,
            Step prepareTargetsStep,
            Step collectStep
    ) {
        return new JobBuilder("aptTradeDataJob", jobRepository)
                .start(prepareTargetsStep)
                .next(collectStep)
                .build();
    }

    @Bean
    public Step prepareTargetsStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   PrepareTargetsTasklet prepareTargetsTasklet
    ) {
        return new StepBuilder("prepareTargetsStep", jobRepository)
                .tasklet(prepareTargetsTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step collectStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager,
                            ItemReader<CollectTarget> collectTargetReader,
                            FetchRawProcessor fetchRawProcessor,
                            RawKeyWriter rawKeyWriter
    ) {
        return new StepBuilder("collectStep", jobRepository)
                .<CollectTarget, RawSaveResult>chunk(1)
                .transactionManager(transactionManager)
                .reader(collectTargetReader)
                .processor(fetchRawProcessor)
                .writer(rawKeyWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<CollectTarget> collectTargetReader(
            @Value("#{jobExecutionContext['collectTargets']}")
            List<String> targets) {

        List<CollectTarget> items = new ArrayList<>();
        for (String target : targets) {
            String[] parts = target.split("\\|");
            items.add(new CollectTarget(parts[0], parts[1] ));
        }

        return new ListItemReader<>(items);
    }
}
