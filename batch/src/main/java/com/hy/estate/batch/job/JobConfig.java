package com.hy.estate.batch.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class JobConfig {

    @Bean
    public Job logTestJob(JobRepository jobRepository, Step logTestStep) {
        return new JobBuilder("logTestJob", jobRepository)
                .start(logTestStep)
                .build();
    }

    @Bean
    public Step logTestStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager) {
        return new StepBuilder("logTestStep", jobRepository)
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
}
