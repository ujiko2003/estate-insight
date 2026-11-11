package com.hy.estate.batch.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

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
                                   PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder("prepareTargetsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String runDate = chunkContext.getStepContext()
                            .getJobParameters()
                            .get("runDate")
                            .toString();
                    System.out.println("runDate = " + runDate);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step collectStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager
    ) {

        return new StepBuilder("collectStep", jobRepository)
                .<String, String>chunk(1)
                .transactionManager(transactionManager)
                .reader(new ListItemReader<>(List.of("11680-202607", "11680-202608", "11680-202609")))
                .processor(item -> item)
                .writer(chunk -> System.out.println("저장할 항목: " + chunk.getItems()))
                .build();
    }
}
